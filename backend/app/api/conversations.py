from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..db import get_session
from ..models import Conversation, Message, Model, User
from ..schemas import (
    ConversationCreate,
    ConversationOut,
    ConversationUpdate,
    MessageOut,
)
from ..services.human import get_or_create_human_conversation

router = APIRouter(prefix="/api")


async def get_me(session: AsyncSession, x_user_id: str | None) -> User | None:
    if not x_user_id:
        return None
    return (
        await session.execute(select(User).where(User.user_id == x_user_id))
    ).scalar_one_or_none()


def conversation_out(
    conv: Conversation, last: Message | None, me_user_id: str | None = None
) -> ConversationOut:
    preview = None
    when = None
    if last is not None:
        preview = (last.content or "")[:50]
        if last.error:
            preview = f"[出错] {preview}"
        when = last.created_at
    if conv.kind == "human":
        peer = (
            conv.peer_b
            if conv.peer_a is not None and conv.peer_a.user_id == me_user_id
            else conv.peer_a
        )
        return ConversationOut(
            id=conv.id,
            kind="human",
            model_id=None,
            contact_name=peer.display_name if peer else "未知用户",
            model_code=peer.user_id if peer else "",
            provider_name="",
            avatar_color=peer.avatar_color if peer else None,
            context_length=None,
            system_prompt=None,
            peer_user_id=peer.user_id if peer else None,
            last_message_preview=preview,
            last_message_time=when,
            updated_at=conv.updated_at,
        )
    return ConversationOut(
        id=conv.id,
        kind="bot",
        model_id=conv.model_id,
        contact_name=conv.model.display_name if conv.model else "已删除模型",
        model_code=conv.model.model_id if conv.model else "",
        provider_name=conv.model.provider.name if conv.model and conv.model.provider else "",
        avatar_color=conv.model.avatar_color if conv.model else None,
        context_length=conv.model.context_length if conv.model else None,
        system_prompt=conv.system_prompt,
        peer_user_id=None,
        last_message_preview=preview,
        last_message_time=when,
        updated_at=conv.updated_at,
    )


async def _last_messages(session: AsyncSession, conversation_ids: list[int]) -> dict[int, Message]:
    """一次查询所有会话的最后一条消息（避免 N+1）。"""
    if not conversation_ids:
        return {}
    sub = (
        select(Message.conversation_id, func.max(Message.id).label("max_id"))
        .where(Message.conversation_id.in_(conversation_ids))
        .group_by(Message.conversation_id)
        .subquery()
    )
    rows = (
        (await session.execute(select(Message).join(sub, Message.id == sub.c.max_id)))
        .scalars()
        .all()
    )
    return {m.conversation_id: m for m in rows}


@router.get("/conversations")
async def list_conversations(
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    me = await get_me(session, x_user_id)
    # bot 会话：启用模型 + 启用服务商
    bot_stmt = (
        select(Conversation)
        .join(Model)
        .options(
            selectinload(Conversation.model).selectinload(Model.provider),
            selectinload(Conversation.peer_a),
            selectinload(Conversation.peer_b),
        )
        .where(Model.is_enabled.is_(True), Model.provider.has(is_enabled=True))
    )
    # human 会话：我参与的单聊
    human_stmt = select(Conversation).options(
        selectinload(Conversation.peer_a),
        selectinload(Conversation.peer_b),
    )
    if me is not None:
        human_stmt = human_stmt.where(
            or_(Conversation.peer_a_id == me.id, Conversation.peer_b_id == me.id)
        )
    else:
        human_stmt = human_stmt.where(False)

    bot_rows = (await session.execute(bot_stmt)).scalars().all()
    human_rows = (await session.execute(human_stmt)).scalars().all()
    convs = [*bot_rows, *human_rows]
    last_by_conv = await _last_messages(session, [c.id for c in convs])

    result = [
        conversation_out(c, last_by_conv.get(c.id), me.user_id if me else None) for c in convs
    ]
    result.sort(key=lambda c: c.last_message_time or c.updated_at, reverse=True)
    return result


@router.post("/conversations")
async def create_conversation(
    body: ConversationCreate,
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    if body.kind == "human":
        me = await get_me(session, x_user_id)
        if me is None:
            raise HTTPException(status_code=400, detail="请先注册自己的身份（X-User-Id）")
        if not body.peer_user_id:
            raise HTTPException(status_code=400, detail="缺少 peer_user_id")
        if body.peer_user_id == me.user_id:
            raise HTTPException(status_code=400, detail="不能与自己聊天")
        peer = (
            await session.execute(select(User).where(User.user_id == body.peer_user_id))
        ).scalar_one_or_none()
        if peer is None:
            raise HTTPException(status_code=404, detail="对方用户不存在，请先添加")
        existing = await get_or_create_human_conversation(session, me, peer)
        await session.commit()
        last = await _last_messages(session, [existing.id])
        return conversation_out(existing, last.get(existing.id), me.user_id)

    # bot：按 model_id 幂等创建（现有逻辑）
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
        last = await _last_messages(session, [existing.id])
        return conversation_out(existing, last.get(existing.id))
    conv = Conversation(kind="bot", model=model)
    session.add(conv)
    await session.commit()
    return conversation_out(conv, None)


@router.get("/conversations/{conversation_id}")
async def get_conversation(
    conversation_id: int,
    session: AsyncSession = Depends(get_session),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
):
    """单个会话信息（聊天窗口进入时用，避免拉全量列表）。"""
    me = await get_me(session, x_user_id)
    conv = (
        await session.execute(
            select(Conversation)
            .options(
                selectinload(Conversation.model).selectinload(Model.provider),
                selectinload(Conversation.peer_a),
                selectinload(Conversation.peer_b),
            )
            .where(Conversation.id == conversation_id)
        )
    ).scalar_one_or_none()
    if conv is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    last = await _last_messages(session, [conv.id])
    return conversation_out(conv, last.get(conv.id), me.user_id if me else None)


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
    if conv.kind != "bot":
        raise HTTPException(status_code=400, detail="只有模型会话支持人设")
    if body.system_prompt is not None:
        conv.system_prompt = body.system_prompt or None
    await session.commit()
    last = await _last_messages(session, [conv.id])
    return conversation_out(conv, last.get(conv.id))


@router.delete("/conversations/{conversation_id}/messages", status_code=204)
async def clear_messages(conversation_id: int, session: AsyncSession = Depends(get_session)):
    conv = await session.get(Conversation, conversation_id)
    if conv is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    await session.execute(
        Message.__table__.delete().where(Message.conversation_id == conversation_id)
    )
    await session.commit()
