from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Provider(Base):
    __tablename__ = "providers"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(64))
    kind: Mapped[str] = mapped_column(String(32))
    base_url: Mapped[str] = mapped_column(String(255))
    api_key: Mapped[str] = mapped_column(String(255), default="")
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )

    models: Mapped[list["Model"]] = relationship(
        back_populates="provider", cascade="all, delete-orphan", order_by="Model.sort"
    )


class Model(Base):
    __tablename__ = "models"
    __table_args__ = (UniqueConstraint("provider_id", "model_id", name="uq_models_provider_model"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    provider_id: Mapped[int] = mapped_column(ForeignKey("providers.id", ondelete="CASCADE"))
    model_id: Mapped[str] = mapped_column(String(128))
    display_name: Mapped[str] = mapped_column(String(64))
    avatar_color: Mapped[str | None] = mapped_column(String(16), nullable=True)
    context_length: Mapped[int | None] = mapped_column(Integer, nullable=True)
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    sort: Mapped[int] = mapped_column(Integer, default=0)

    provider: Mapped["Provider"] = relationship(back_populates="models")
    conversations: Mapped[list["Conversation"]] = relationship(
        back_populates="model", cascade="all, delete-orphan"
    )


class User(Base):
    """人人聊天的人联系人（自托管无登录，user_id 即身份）。"""

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[str] = mapped_column(String(64), unique=True)
    display_name: Mapped[str] = mapped_column(String(64))
    avatar_color: Mapped[str | None] = mapped_column(String(16), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    conversations_a: Mapped[list["Conversation"]] = relationship(
        back_populates="peer_a", foreign_keys="Conversation.peer_a_id"
    )
    conversations_b: Mapped[list["Conversation"]] = relationship(
        back_populates="peer_b", foreign_keys="Conversation.peer_b_id"
    )


class Conversation(Base):
    """会话：kind=bot（模型联系人）或 human（人人单聊）。

    部分唯一索引保证：bot 会话每个模型一条；human 会话每对用户一条（幂等）。
    """

    __tablename__ = "conversations"
    __table_args__ = (
        Index(
            "uq_conversations_model",
            "model_id",
            unique=True,
            sqlite_where=text("model_id IS NOT NULL"),
        ),
        Index(
            "uq_conversations_peer",
            "peer_a_id",
            "peer_b_id",
            unique=True,
            sqlite_where=text("peer_a_id IS NOT NULL"),
        ),
        Index("ix_conversations_peer_a", "peer_a_id"),
        Index("ix_conversations_peer_b", "peer_b_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    kind: Mapped[str] = mapped_column(String(8), default="bot")
    model_id: Mapped[int | None] = mapped_column(
        ForeignKey("models.id", ondelete="CASCADE"), nullable=True
    )
    peer_a_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), nullable=True
    )
    peer_b_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), nullable=True
    )
    system_prompt: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow
    )

    model: Mapped["Model | None"] = relationship(back_populates="conversations")
    peer_a: Mapped["User | None"] = relationship(
        back_populates="conversations_a", foreign_keys=[peer_a_id]
    )
    peer_b: Mapped["User | None"] = relationship(
        back_populates="conversations_b", foreign_keys=[peer_b_id]
    )
    messages: Mapped[list["Message"]] = relationship(
        back_populates="conversation", cascade="all, delete-orphan"
    )


class Message(Base):
    __tablename__ = "messages"
    __table_args__ = (Index("ix_messages_conversation", "conversation_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    conversation_id: Mapped[int] = mapped_column(ForeignKey("conversations.id", ondelete="CASCADE"))
    role: Mapped[str] = mapped_column(String(16))
    content: Mapped[str] = mapped_column(Text)
    model_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    sender_user_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    completion_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    error: Mapped[str | None] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    conversation: Mapped["Conversation"] = relationship(back_populates="messages")
