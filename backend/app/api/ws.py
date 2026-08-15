"""WebSocket 实时通道（独立于 AI 的 SSE 链路，POST /api/chat 不受影响）。

用法：
- 连接：ws://<host>/api/ws?client_id=<用户标识>  （MVP 无用户体系，客户端握手时自报身份）
- 心跳：客户端发 {"type":"ping"} → 服务端回 {"type":"pong"}
- 发消息：{"type":"message","to":"<对方 client_id>","content":"..."}
  → 服务端转发给接收方全部连接：
    {"type":"message","from":"<发送方>","content":"..."}
  → 对方不在线时回复 {"type":"error","code":"offline","message":"对方不在线"}
- 未知类型：{"type":"error","code":"bad_request","message":"..."}
"""

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from ..services.ws import manager

router = APIRouter()


@router.websocket("/api/ws")
async def ws_channel(websocket: WebSocket, client_id: str = Query(..., min_length=1)):
    await websocket.accept()
    await manager.connect(client_id, websocket)
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
                delivered = await manager.send_to(
                    to, {"type": "message", "from": client_id, "content": content}
                )
                if not delivered:
                    await websocket.send_json(
                        {"type": "error", "code": "offline", "message": "对方不在线"}
                    )
            else:
                await websocket.send_json(
                    {"type": "error", "code": "bad_request", "message": f"未知消息类型：{kind}"}
                )
    except WebSocketDisconnect:
        pass
    finally:
        await manager.disconnect(client_id, websocket)
