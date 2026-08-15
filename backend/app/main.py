from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

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
app.mount("/avatars", StaticFiles(directory=_avatars_dir), name="avatars")


@app.get("/api/health")
async def health():
    return {"status": "ok"}
