import logging
import time
from dataclasses import dataclass

from fastapi import HTTPException
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..config import settings
from ..models import Conversation, Message, Model, Provider, utcnow
from ..providers import (
    ChatRequest,
    Done,
    OpenAICompatAdapter,
    ProviderError,
    TokenDelta,
)

logger = logging.getLogger(__name__)


@dataclass
class PreparedChat:
    conversation: Conversation
    model: Model
    provider: Provider
    request: ChatRequest


async def prepare_chat(
    session: AsyncSession,
    conversation_id: int,
    content: str,
    *,
    regenerate: bool = False,
) -> PreparedChat:
    conv = await session.get(
        Conversation,
        conversation_id,
        options=[selectinload(Conversation.model).selectinload(Model.provider)],
    )
    if conv is None or conv.model is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    model = conv.model
    provider = model.provider
    if not provider.is_enabled or not model.is_enabled:
        raise HTTPException(status_code=400, detail="联系人或服务商已停用")

    stored_messages = (
        (
            await session.execute(
                select(Message).where(Message.conversation_id == conv.id).order_by(Message.id)
            )
        )
        .scalars()
        .all()
    )

    if regenerate:
        last_user = next(
            (message for message in reversed(stored_messages) if message.role == "user"),
            None,
        )
        if last_user is None:
            raise HTTPException(status_code=409, detail="没有可重新生成的用户消息")
        if last_user.content != content:
            raise HTTPException(status_code=409, detail="最后一条用户消息已变化，请刷新后重试")
        await session.execute(
            delete(Message).where(
                Message.conversation_id == conv.id,
                Message.id > last_user.id,
            )
        )
        history = [message for message in stored_messages if message.id <= last_user.id]
    else:
        history = stored_messages

    messages: list[dict[str, str]] = []
    if conv.system_prompt:
        messages.append({"role": "system", "content": conv.system_prompt})
    for msg in history:
        if msg.role in ("user", "assistant") and msg.error is None:
            messages.append({"role": msg.role, "content": msg.content})
    if not regenerate:
        messages.append({"role": "user", "content": content})
        session.add(
            Message(
                conversation_id=conv.id,
                role="user",
                content=content,
                model_id=model.model_id,
            )
        )
    conv.updated_at = utcnow()
    await session.commit()

    request = ChatRequest(
        base_url=provider.base_url,
        api_key=provider.api_key,
        model=model.model_id,
        messages=messages,
        timeout_first_token=settings.timeout_first_token,
        timeout_total=settings.timeout_total,
    )
    return PreparedChat(conversation=conv, model=model, provider=provider, request=request)


async def stream_chat(session: AsyncSession, prepared: PreparedChat):
    adapter = OpenAICompatAdapter()
    parts: list[str] = []
    usage: dict | None = None
    error: ProviderError | None = None
    started = time.monotonic()
    finished = False
    try:
        async for event in adapter.chat_stream(prepared.request):
            if isinstance(event, TokenDelta):
                parts.append(event.delta)
                yield {"type": "token", "delta": event.delta}
            elif isinstance(event, Done):
                usage = event.usage
            elif isinstance(event, ProviderError):
                error = event
        finished = True
    finally:
        if not finished:
            session.add(
                Message(
                    conversation_id=prepared.conversation.id,
                    role="assistant",
                    content="".join(parts),
                    model_id=prepared.model.model_id,
                    error="interrupted: 生成已停止",
                )
            )
            prepared.conversation.updated_at = utcnow()
            try:
                await session.commit()
            except Exception:
                logger.exception(
                    "Failed to persist interrupted assistant message for conversation %s",
                    prepared.conversation.id,
                )

    duration_ms = int((time.monotonic() - started) * 1000)
    content_full = "".join(parts)

    if error is not None:
        session.add(
            Message(
                conversation_id=prepared.conversation.id,
                role="assistant",
                content=content_full,
                model_id=prepared.model.model_id,
                duration_ms=duration_ms,
                error=f"{error.code}: {error.message}"[:500],
            )
        )
        prepared.conversation.updated_at = utcnow()
        await session.commit()
        yield {"type": "error", "code": error.code, "message": error.message}
        return

    assistant = Message(
        conversation_id=prepared.conversation.id,
        role="assistant",
        content=content_full,
        model_id=prepared.model.model_id,
        prompt_tokens=(usage or {}).get("prompt_tokens"),
        completion_tokens=(usage or {}).get("completion_tokens"),
        duration_ms=duration_ms,
    )
    session.add(assistant)
    prepared.conversation.updated_at = utcnow()
    await session.commit()
    yield {"type": "done", "message_id": assistant.id, "usage": usage or {}}
