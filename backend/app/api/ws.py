"""WebSocket 实时通道（人人通信，独立于 AI 的 SSE 链路）。

连接：ws://<host>/api/ws?user_id=<身份>  （自托管无登录，user_id 即身份）

上行（客户端 → 服务端）：
- {"type":"ping"}                                  心跳，服务端回 pong
- {"type":"message","to":"<user_id>","content":"..."}  发消息（落库 + 转发 + 回执）
- {"type":"typing","to":"<user_id>"}               正在输入提示
- {"type":"read","to":"<user_id>"}                 已读回执

下行（服务端 → 客户端）：
- {"type":"pong"}
- {"type":"sync"}  连接建立后通知客户端刷新列表/消息（离线消息由此补齐）
- {"type":"message","from":"...","content":"...","message_id":N,"conversation_id":N,"ts":"..."}
- {"type":"ack","message_id":N,"conversation_id":N,"delivered":bool}   发送方确认（已送达）
- {"type":"typing","from":"..."} / {"type":"read","from":"..."}
- {"type":"online","user_id":"...","online":bool}  会话对方的上下线
- {"type":"error","code":"...","message":"..."}
"""

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect
from sqlalchemy import select

from ..db import SessionLocal
from ..models import Message, User, utcnow
from ..services.human import get_or_create_human_conversation, human_peer_user_ids
from ..services.ws import manager

router = APIRouter()


async def _get_user(user_id: str):
    async with SessionLocal() as session:
        return (
            await session.execute(select(User).where(User.user_id == user_id))
        ).scalar_one_or_none()


async def _notify_online(user_id: str, online: bool) -> None:
    """通知我所有 human 会话的对方：我上线/下线了。"""
    async with SessionLocal() as session:
        me = (
            await session.execute(select(User).where(User.user_id == user_id))
        ).scalar_one_or_none()
        if me is None:
            return
        peers = await human_peer_user_ids(session, me)
    for peer_id in peers:
        await manager.send_to(peer_id, {"type": "online", "user_id": user_id, "online": online})


async def _handle_message(me_user_id: str, to_user_id: str, content: str) -> None:
    """落库一条 human 消息并转发给接收方，回执给发送方。"""
    async with SessionLocal() as session:
        me = (
            await session.execute(select(User).where(User.user_id == me_user_id))
        ).scalar_one_or_none()
        peer = (
            await session.execute(select(User).where(User.user_id == to_user_id))
        ).scalar_one_or_none()
        if me is None or peer is None:
            await manager.send_to(
                me_user_id,
                {"type": "error", "code": "not_found", "message": "对方用户不存在"},
            )
            return
        conv = await get_or_create_human_conversation(session, me, peer)
        msg = Message(
            conversation_id=conv.id,
            role="user",
            content=content,
            sender_user_id=me_user_id,
        )
        session.add(msg)
        conv.updated_at = utcnow()
        await session.commit()
        message_id = msg.id
        conv_id = conv.id
        ts = msg.created_at.isoformat()

    delivered = await manager.send_to(
        to_user_id,
        {
            "type": "message",
            "from": me_user_id,
            "content": content,
            "message_id": message_id,
            "conversation_id": conv_id,
            "ts": ts,
        },
    )
    await manager.send_to(
        me_user_id,
        {
            "type": "ack",
            "message_id": message_id,
            "conversation_id": conv_id,
            "delivered": delivered,
        },
    )


async def _forward_event(kind: str, me_user_id: str, to_user_id: str) -> None:
    """转发 typing/read 事件给目标用户（不落库）。"""
    await manager.send_to(to_user_id, {"type": kind, "from": me_user_id})


@router.websocket("/api/ws")
async def ws_channel(websocket: WebSocket, user_id: str = Query(..., min_length=1)):
    me = await _get_user(user_id)
    if me is None:
        await websocket.close(code=4001, reason="身份未注册")
        return
    await websocket.accept()
    await manager.connect(user_id, websocket)
    await websocket.send_json({"type": "sync"})  # 只发给当前连接（不广播给同用户其他连接）
    await _notify_online(user_id, True)
    try:
        while True:
            data = await websocket.receive_json()
            kind = data.get("type")
            if kind == "ping":
                await websocket.send_json({"type": "pong"})
            elif kind == "message":
                to = str(data.get("to") or "").strip()
                content = str(data.get("content") or "").strip()
                if not to or not content:
                    await websocket.send_json(
                        {"type": "error", "code": "bad_request", "message": "缺少 to 或 content"}
                    )
                    continue
                await _handle_message(user_id, to, content)
            elif kind in ("typing", "read"):
                to = str(data.get("to") or "").strip()
                if to:
                    await _forward_event(kind, user_id, to)
            else:
                await websocket.send_json(
                    {"type": "error", "code": "bad_request", "message": f"未知消息类型：{kind}"}
                )
    except WebSocketDisconnect:
        pass
    finally:
        await manager.disconnect(user_id, websocket)
        await _notify_online(user_id, False)
