from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..db import get_session
from ..models import Conversation, Message, Model
from ..schemas import (
    ConversationCreate,
    ConversationOut,
    ConversationUpdate,
    MessageOut,
)

router = APIRouter(prefix="/api")


def conversation_out(conv: Conversation, last: Message | None) -> ConversationOut:
    preview = None
    when = None
    if last is not None:
        preview = (last.content or "")[:50]
        if last.error:
            preview = f"[出错] {preview}"
        when = last.created_at
    return ConversationOut(
        id=conv.id,
        model_id=conv.model_id,
        contact_name=conv.model.display_name,
        model_code=conv.model.model_id,
        provider_name=conv.model.provider.name,
        avatar_color=conv.model.avatar_color,
        context_length=conv.model.context_length,
        system_prompt=conv.system_prompt,
        last_message_preview=preview,
        last_message_time=when,
        updated_at=conv.updated_at,
    )


@router.get("/conversations")
async def list_conversations(session: AsyncSession = Depends(get_session)):
    convs = (
        (
            await session.execute(
                select(Conversation)
                .join(Model)
                .options(selectinload(Conversation.model).selectinload(Model.provider))
                .where(Model.is_enabled.is_(True), Model.provider.has(is_enabled=True))
                .order_by(Conversation.id)
            )
        )
        .scalars()
        .all()
    )
    result = []
    for conv in convs:
        last = (
            await session.execute(
                select(Message)
                .where(Message.conversation_id == conv.id)
                .order_by(Message.id.desc())
                .limit(1)
            )
        ).scalar_one_or_none()
        result.append(conversation_out(conv, last))
    result.sort(key=lambda c: c.last_message_time or c.updated_at, reverse=True)
    return result


@router.post("/conversations")
async def create_conversation(
    body: ConversationCreate, session: AsyncSession = Depends(get_session)
):
    model = await session.get(Model, body.model_id, options=[selectinload(Model.provider)])
    if model is None:
        raise HTTPException(status_code=404, detail="模型不存在")
    existing = (
        await session.execute(
            select(Conversation)
            .options(selectinload(Conversation.model).selectinload(Model.provider))
            .where(Conversation.model_id == body.model_id)
        )
    ).scalar_one_or_none()
    if existing is not None:
        last = (
            await session.execute(
                select(Message)
                .where(Message.conversation_id == existing.id)
                .order_by(Message.id.desc())
                .limit(1)
            )
        ).scalar_one_or_none()
        return conversation_out(existing, last)
    conv = Conversation(model=model)
    session.add(conv)
    await session.commit()
    return conversation_out(conv, None)


@router.get("/conversations/{conversation_id}/messages")
async def list_messages(conversation_id: int, session: AsyncSession = Depends(get_session)):
    conv = await session.get(Conversation, conversation_id)
    if conv is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    rows = (
        (
            await session.execute(
                select(Message)
                .where(Message.conversation_id == conversation_id)
                .order_by(Message.id)
            )
        )
        .scalars()
        .all()
    )
    return [MessageOut.model_validate(m) for m in rows]


@router.put("/conversations/{conversation_id}")
async def update_conversation(
    conversation_id: int,
    body: ConversationUpdate,
    session: AsyncSession = Depends(get_session),
):
    conv = await session.get(
        Conversation,
        conversation_id,
        options=[selectinload(Conversation.model).selectinload(Model.provider)],
    )
    if conv is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    if body.system_prompt is not None:
        conv.system_prompt = body.system_prompt or None
    await session.commit()
    last = (
        await session.execute(
            select(Message)
            .where(Message.conversation_id == conv.id)
            .order_by(Message.id.desc())
            .limit(1)
        )
    ).scalar_one_or_none()
    return conversation_out(conv, last)


@router.delete("/conversations/{conversation_id}/messages", status_code=204)
async def clear_messages(conversation_id: int, session: AsyncSession = Depends(get_session)):
    conv = await session.get(Conversation, conversation_id)
    if conv is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    await session.execute(
        Message.__table__.delete().where(Message.conversation_id == conversation_id)
    )
    await session.commit()
