import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, Header, HTTPException, UploadFile
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import settings
from ..db import get_session
from ..models import User
from ..schemas import UserCreate, UserOut, UserUpdate

router = APIRouter(prefix="/api")

# 头像上传：仅接受常见图片类型 + 大小上限 5MB（自托管 MVP，类型/大小双校验）
_AVATAR_EXT = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}
_AVATAR_MAX_BYTES = 5 * 1024 * 1024
_MAGIC = {
    b"\x89PNG\r\n\x1a\n": ".png",
    b"\xff\xd8\xff": ".jpg",
    b"RIFF": ".webp",  # 再校验尾部 WEBP 标识
}


def avatars_dir() -> Path:
    return Path(settings.db_path).parent / "avatars"


def avatar_url(user: User) -> str | None:
    return f"/avatars/{user.avatar}" if user.avatar else None


def user_out(user: User) -> UserOut:
    return UserOut(
        id=user.id,
        user_id=user.user_id,
        display_name=user.display_name,
        avatar_color=user.avatar_color,
        avatar=avatar_url(user),
    )


@router.post("/users", status_code=201)
async def register_user(body: UserCreate, session: AsyncSession = Depends(get_session)):
    """注册身份（幂等）：已存在则返回现有记录，显示名以首次注册为准。"""
    existing = (
        await session.execute(select(User).where(User.user_id == body.user_id))
    ).scalar_one_or_none()
    if existing is not None:
        return user_out(existing)
    user = User(
        user_id=body.user_id,
        display_name=body.display_name or body.user_id,
        avatar_color=body.avatar_color,
    )
    session.add(user)
    await session.commit()
    return user_out(user)


@router.get("/users/{user_id}")
async def search_user(user_id: str, session: AsyncSession = Depends(get_session)):
    user = (await session.execute(select(User).where(User.user_id == user_id))).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="用户不存在")
    return user_out(user)


@router.get("/me")
async def get_me(
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    if not x_user_id:
        raise HTTPException(status_code=400, detail="缺少 X-User-Id 头")
    user = (
        await session.execute(select(User).where(User.user_id == x_user_id))
    ).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="身份未注册，请先 POST /api/users")
    return user_out(user)


@router.put("/me")
async def update_me(
    body: UserUpdate,
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    if not x_user_id:
        raise HTTPException(status_code=400, detail="缺少 X-User-Id 头")
    user = (
        await session.execute(select(User).where(User.user_id == x_user_id))
    ).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="身份未注册，请先 POST /api/users")
    if body.display_name is not None:
        name = body.display_name.strip()
        if not name:
            raise HTTPException(status_code=400, detail="显示名不能为空")
        user.display_name = name
    if body.avatar_color is not None:
        user.avatar_color = body.avatar_color
    await session.commit()
    return user_out(user)


@router.post("/me/avatar")
async def upload_avatar(
    file: UploadFile = File(...),
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    """上传我的头像：multipart 文件，校验类型（JPG/PNG/WebP）与大小（≤5MB）。

    保存到 data/avatars/（随机文件名），替换旧头像，返回头像 URL 路径。
    """
    if not x_user_id:
        raise HTTPException(status_code=400, detail="缺少 X-User-Id 头")
    me = (
        await session.execute(select(User).where(User.user_id == x_user_id))
    ).scalar_one_or_none()
    if me is None:
        raise HTTPException(status_code=404, detail="身份未注册，请先 POST /api/users")

    content_type = (file.content_type or "").lower()
    if content_type not in _AVATAR_EXT:
        raise HTTPException(status_code=400, detail="仅支持 JPG/PNG/WebP 图片")
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="文件为空")
    if len(data) > _AVATAR_MAX_BYTES:
        raise HTTPException(status_code=400, detail="图片不能超过 5MB")
    # 魔数兜底：content_type 可伪造，按文件头校验真实格式
    magic_ext = None
    for sig, ext in _MAGIC.items():
        if data.startswith(sig):
            if sig == b"RIFF":
                magic_ext = ext if data[8:12] == b"WEBP" else None
            else:
                magic_ext = ext
            break
    if magic_ext != _AVATAR_EXT.get(content_type):
        raise HTTPException(status_code=400, detail="图片内容与格式不符")

    avatars = avatars_dir()
    avatars.mkdir(parents=True, exist_ok=True)
    filename = f"{uuid.uuid4().hex}{magic_ext}"
    # 替换旧头像：先落新文件再删旧文件，避免删旧失败丢失头像
    (avatars / filename).write_bytes(data)
    if me.avatar:
        old = avatars / Path(me.avatar).name
        try:
            old.unlink(missing_ok=True)
        except OSError:
            pass
    me.avatar = filename
    await session.commit()
    return user_out(me)
