"""人人会话公共逻辑（REST 与 WebSocket 复用）。"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..models import Conversation, User


async def get_or_create_human_conversation(
    session: AsyncSession, me: User, peer: User
) -> Conversation:
    """两人之间幂等创建会话（peer_a_id < peer_b_id，部分唯一索引保证唯一）。"""
    a_id, b_id = sorted((me.id, peer.id))
    conv = (
        await session.execute(
            select(Conversation)
            .options(selectinload(Conversation.peer_a), selectinload(Conversation.peer_b))
            .where(
                Conversation.peer_a_id == a_id,
                Conversation.peer_b_id == b_id,
            )
        )
    ).scalar_one_or_none()
    if conv is None:
        conv = Conversation(kind="human", peer_a_id=a_id, peer_b_id=b_id)
        session.add(conv)
        await session.flush()
        conv = (
            await session.execute(
                select(Conversation)
                .options(selectinload(Conversation.peer_a), selectinload(Conversation.peer_b))
                .where(Conversation.id == conv.id)
            )
        ).scalar_one()
    return conv


async def human_peer_user_ids(session: AsyncSession, me: User) -> list[str]:
    """我参与的所有 human 会话的对方 user_id（用于在线状态通知）。"""
    from sqlalchemy import or_

    rows = (
        (
            await session.execute(
                select(Conversation).where(
                    or_(Conversation.peer_a_id == me.id, Conversation.peer_b_id == me.id)
                )
            )
        )
        .scalars()
        .all()
    )
    peers: list[str] = []
    for conv in rows:
        if conv.peer_a_id == me.id:
            peer_id = conv.peer_b_id
        else:
            peer_id = conv.peer_a_id
        if peer_id is not None:
            user = await session.get(User, peer_id)
            if user is not None:
                peers.append(user.user_id)
    return peers
