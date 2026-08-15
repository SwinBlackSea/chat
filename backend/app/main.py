from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .api import chat, conversations, providers, ws
from .db import init_db


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(title="ChatHub API", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(providers.router)
app.include_router(conversations.router)
app.include_router(chat.router)
app.include_router(ws.router)


@app.get("/api/health")
async def health():
    return {"status": "ok"}
