import asyncio
import sqlite3
from pathlib import Path

from sqlalchemy import event
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from .config import settings


class Base(DeclarativeBase):
    pass


def _database_url() -> str:
    return f"sqlite+aiosqlite:///{settings.db_path}"


engine = create_async_engine(_database_url(), echo=False)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)


@event.listens_for(engine.sync_engine, "connect")
def _sqlite_pragmas(dbapi_conn, _record) -> None:
    cursor = dbapi_conn.cursor()
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.close()


# 兼容迁移：旧结构（conversations 无 kind、messages 无 sender_user_id）→ 新结构。
# 仅当旧库存在时重建两个表（保留 id，消息关联不变），不丢服务商/消息数据。
_CONVERSATIONS_DDL = """
CREATE TABLE conversations (
    id INTEGER NOT NULL PRIMARY KEY,
    kind VARCHAR(8) NOT NULL,
    model_id INTEGER,
    peer_a_id INTEGER,
    peer_b_id INTEGER,
    system_prompt TEXT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_conversations_model_id_models
        FOREIGN KEY(model_id) REFERENCES models(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_peer_a_id_users
        FOREIGN KEY(peer_a_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_peer_b_id_users
        FOREIGN KEY(peer_b_id) REFERENCES users(id) ON DELETE CASCADE
)
"""

_MESSAGES_DDL = """
CREATE TABLE messages (
    id INTEGER NOT NULL PRIMARY KEY,
    conversation_id INTEGER NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    model_id VARCHAR(128),
    sender_user_id VARCHAR(64),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    duration_ms INTEGER,
    error VARCHAR(512),
    created_at DATETIME,
    CONSTRAINT fk_messages_conversation_id_conversations
        FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
)
"""

_INDEX_SQL = [
    "CREATE INDEX IF NOT EXISTS ix_messages_conversation ON messages(conversation_id)",
    "CREATE INDEX IF NOT EXISTS ix_conversations_peer_a ON conversations(peer_a_id)",
    "CREATE INDEX IF NOT EXISTS ix_conversations_peer_b ON conversations(peer_b_id)",
    "CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_model"
    " ON conversations(model_id) WHERE model_id IS NOT NULL",
    "CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_peer"
    " ON conversations(peer_a_id, peer_b_id) WHERE peer_a_id IS NOT NULL",
]


def _migrate_schema_sync(db_path: str) -> None:
    """旧库升级（原生 sqlite3，启动时执行一次）：conversations 加 kind/peer 列、
    messages 加 sender_user_id、补索引；保留 id，消息关联不变，不丢数据。"""
    conn = sqlite3.connect(db_path)
    try:
        conn.execute("PRAGMA foreign_keys=OFF")
        conv_cols = {row[1] for row in conn.execute("PRAGMA table_info(conversations)")}
        msg_cols = {row[1] for row in conn.execute("PRAGMA table_info(messages)")}
        if conv_cols and "kind" not in conv_cols:
            # 旧 conversations（model_id 非空唯一）→ 新结构（bot/human 双类型会话）
            conn.execute("ALTER TABLE conversations RENAME TO conversations_old")
            conn.execute("ALTER TABLE messages RENAME TO messages_old")
            conn.execute(_CONVERSATIONS_DDL)
            conn.execute(_MESSAGES_DDL)
            conn.execute(
                "INSERT INTO conversations (id, kind, model_id, system_prompt,"
                " created_at, updated_at) "
                "SELECT id, 'bot', model_id, system_prompt, created_at, updated_at "
                "FROM conversations_old"
            )
            conn.execute(
                "INSERT INTO messages (id, conversation_id, role, content, model_id, "
                "prompt_tokens, completion_tokens, duration_ms, error, created_at) "
                "SELECT id, conversation_id, role, content, model_id, "
                "prompt_tokens, completion_tokens, duration_ms, error, created_at "
                "FROM messages_old"
            )
            conn.execute("DROP TABLE messages_old")
            conn.execute("DROP TABLE conversations_old")
        elif msg_cols and "sender_user_id" not in msg_cols:
            # 中间态：conversations 已是新结构，仅补 messages 列
            conn.execute("ALTER TABLE messages ADD COLUMN sender_user_id VARCHAR(64)")
        # 头像列（users）：新库 create_all 已建，旧库这里补
        user_cols = {row[1] for row in conn.execute("PRAGMA table_info(users)")}
        if user_cols and "avatar" not in user_cols:
            conn.execute("ALTER TABLE users ADD COLUMN avatar VARCHAR(255)")
        for sql in _INDEX_SQL:
            conn.execute(sql)
        conn.commit()
    finally:
        conn.close()


async def _migrate_schema() -> None:
    await asyncio.to_thread(_migrate_schema_sync, settings.db_path)


async def init_db() -> None:
    from . import models  # noqa: F401

    Path(settings.db_path).parent.mkdir(parents=True, exist_ok=True)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await conn.exec_driver_sql("PRAGMA journal_mode=WAL")
    await _migrate_schema()


async def get_session():
    async with SessionLocal() as session:
        yield session
