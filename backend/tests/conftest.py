import os
import tempfile

# 必须在 import app 之前设置：ws 测试（TestClient + lifespan）使用的全局 engine 指向
# 独立测试库，避免触碰开发/生产 data/chat.db。
os.environ.setdefault("CHAT_DB_PATH", os.path.join(tempfile.gettempdir(), "chathub-pytest.db"))

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.db import Base, get_session
from app.main import app


@pytest_asyncio.fixture
async def client():
    engine = create_async_engine("sqlite+aiosqlite:///:memory:")
    testing_session = async_sessionmaker(engine, expire_on_commit=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async def override_get_session():
        async with testing_session() as session:
            yield session

    app.dependency_overrides[get_session] = override_get_session
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()
    await engine.dispose()
