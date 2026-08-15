"""兼容迁移测试：旧结构库（无 kind / sender_user_id）升级到新结构，数据不丢。"""

import sqlite3

from app.db import _migrate_schema_sync

OLD_CONVERSATIONS = """
CREATE TABLE conversations (
    id INTEGER NOT NULL PRIMARY KEY,
    model_id INTEGER NOT NULL,
    system_prompt TEXT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_conversations_model_id_models FOREIGN KEY(model_id) REFERENCES models(id)
);
"""
OLD_MESSAGES = """
CREATE TABLE messages (
    id INTEGER NOT NULL PRIMARY KEY,
    conversation_id INTEGER NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    model_id VARCHAR(128),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    duration_ms INTEGER,
    error VARCHAR(512),
    created_at DATETIME,
    CONSTRAINT fk_messages_conversation_id_conversations
        FOREIGN KEY(conversation_id) REFERENCES conversations(id)
);
"""


def test_migrate_old_schema_keeps_data(tmp_path):
    db = tmp_path / "old.db"
    conn = sqlite3.connect(db)
    conn.executescript(
        OLD_CONVERSATIONS
        + OLD_MESSAGES
        + """
        INSERT INTO conversations (id, model_id, system_prompt, created_at, updated_at)
        VALUES (1, 7, '你是个助手', '2026-01-01', '2026-01-02');
        INSERT INTO messages (id, conversation_id, role, content, model_id, created_at)
        VALUES (1, 1, 'user', '你好', 'deepseek-chat', '2026-01-01');
        INSERT INTO messages (id, conversation_id, role, content, model_id, created_at)
        VALUES (2, 1, 'assistant', '你好呀', 'deepseek-chat', '2026-01-01');
        """
    )
    conn.commit()
    conn.close()

    _migrate_schema_sync(str(db))

    conn = sqlite3.connect(db)
    conv_cols = {row[1] for row in conn.execute("PRAGMA table_info(conversations)")}
    msg_cols = {row[1] for row in conn.execute("PRAGMA table_info(messages)")}
    assert "kind" in conv_cols
    assert "peer_a_id" in conv_cols
    assert "peer_b_id" in conv_cols
    assert "sender_user_id" in msg_cols

    rows = conn.execute("SELECT id, kind, model_id, system_prompt FROM conversations").fetchall()
    assert rows == [(1, "bot", 7, "你是个助手")]
    msgs = conn.execute(
        "SELECT id, conversation_id, role, content FROM messages ORDER BY id"
    ).fetchall()
    assert msgs == [(1, 1, "user", "你好"), (2, 1, "assistant", "你好呀")]

    indexes = {row[1] for row in conn.execute("PRAGMA index_list(conversations)")}
    assert "ix_conversations_peer_a" in indexes
    msg_indexes = {row[1] for row in conn.execute("PRAGMA index_list(messages)")}
    assert "ix_messages_conversation" in msg_indexes
    conn.close()


def test_migrate_new_schema_is_noop(tmp_path):
    """新结构库迁移是幂等 no-op（仅补索引）。"""
    db = tmp_path / "new.db"
    conn = sqlite3.connect(db)
    conn.executescript(
        """
        CREATE TABLE conversations (
            id INTEGER NOT NULL PRIMARY KEY,
            kind VARCHAR(8) NOT NULL,
            model_id INTEGER,
            peer_a_id INTEGER,
            peer_b_id INTEGER,
            system_prompt TEXT,
            created_at DATETIME,
            updated_at DATETIME
        );
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
            created_at DATETIME
        );
        INSERT INTO conversations (id, kind, model_id, system_prompt)
        VALUES (1, 'bot', 3, 'x');
        INSERT INTO messages (id, conversation_id, role, content) VALUES (1, 1, 'user', 'hi');
        """
    )
    conn.commit()
    conn.close()

    _migrate_schema_sync(str(db))

    conn = sqlite3.connect(db)
    rows = conn.execute("SELECT id, kind, model_id FROM conversations").fetchall()
    assert rows == [(1, "bot", 3)]
    assert conn.execute("SELECT COUNT(*) FROM messages").fetchone()[0] == 1
    conn.close()
