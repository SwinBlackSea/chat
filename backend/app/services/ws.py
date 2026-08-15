"""WebSocket 实时通道连接管理（人人通信通道，独立于 AI 的 SSE 链路）。

内存注册表：client_id → 活跃 WebSocket 连接集合（一个用户可多端在线）。
MVP 无用户体系，client_id 由客户端握手时传入；后续接入 users 表后改为
鉴权后从会话推导。
"""

import asyncio
import logging

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    def __init__(self) -> None:
        self._connections: dict[str, set[WebSocket]] = {}
        self._lock = asyncio.Lock()

    async def connect(self, client_id: str, ws: WebSocket) -> None:
        async with self._lock:
            self._connections.setdefault(client_id, set()).add(ws)
        logger.info("ws connected: client=%s online=%d", client_id, len(self._connections))

    async def disconnect(self, client_id: str, ws: WebSocket) -> None:
        async with self._lock:
            conns = self._connections.get(client_id)
            if conns:
                conns.discard(ws)
                if not conns:
                    self._connections.pop(client_id, None)
        logger.info("ws disconnected: client=%s online=%d", client_id, len(self._connections))

    def is_online(self, client_id: str) -> bool:
        return bool(self._connections.get(client_id))

    async def send_to(self, client_id: str, payload: dict) -> bool:
        """定向推送：发给该用户的所有连接；失败连接即清理。返回是否至少送达一个。"""
        delivered = False
        for ws in list(self._connections.get(client_id, ())):
            try:
                await ws.send_json(payload)
                delivered = True
            except Exception:
                logger.exception("ws send failed, dropping client=%s", client_id)
                await self.disconnect(client_id, ws)
        return delivered

    async def broadcast(self, payload: dict) -> None:
        for client_id in list(self._connections):
            await self.send_to(client_id, payload)

    def online_count(self) -> int:
        return len(self._connections)


manager = ConnectionManager()
