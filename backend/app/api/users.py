from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_session
from ..models import User
from ..schemas import UserCreate, UserOut, UserUpdate

router = APIRouter(prefix="/api")


def user_out(user: User) -> UserOut:
    return UserOut(
        id=user.id,
        user_id=user.user_id,
        display_name=user.display_name,
        avatar_color=user.avatar_color,
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
