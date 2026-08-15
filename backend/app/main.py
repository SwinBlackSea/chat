from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from .api import chat, conversations, providers, users, ws
from .config import settings
from .db import init_db

# 头像静态目录：data/avatars/（与 db 同目录），POST /api/me/avatar 写入、/avatars/* 读取
_avatars_dir = Path(settings.db_path).parent / "avatars"
_avatars_dir.mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(title="ChatHub API", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(providers.router)
app.include_router(conversations.router)
app.include_router(chat.router)
app.include_router(users.router)
app.include_router(ws.router)


@app.api_route("/avatars/{name}", methods=["GET", "HEAD"], include_in_schema=False)
async def avatar_file(name: str):
    """头像文件：URL 带 ?v=<mtime_ns> 版本（换头像即新文件新 URL），
    文件内容不变的 URL 可安全长期缓存，因此返回 immutable 一年缓存；
    换头像后新 URL 会重新拉取，不存在缓存旧图问题。"""
    filename = Path(name).name  # 只取文件名，防路径穿越
    path = _avatars_dir / filename
    if not path.is_file():
        raise HTTPException(status_code=404, detail="头像不存在")
    return FileResponse(
        path,
        headers={"Cache-Control": "public, max-age=31536000, immutable"},
    )


@app.get("/api/health")
async def health():
    return {"status": "ok"}
