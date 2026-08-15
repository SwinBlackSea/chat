"""WebSocket 实时通道测试（人人通信；独立于 AI SSE 链路）。

用真实 uvicorn 服务器（独立测试库）+ websockets 客户端，规避 TestClient
独立线程循环与 aiosqlite 连接清理的冲突。
"""

import json
import socket
import threading
import time

import httpx
import pytest
import uvicorn
from websockets.sync.client import connect as ws_connect

from app.main import app


def _free_port() -> int:
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


@pytest.fixture(scope="module")
def server():
    port = _free_port()
    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning")
    srv = uvicorn.Server(config)
    thread = threading.Thread(target=srv.run, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{port}"
    for _ in range(200):
        try:
            httpx.get(f"{base}/api/health", timeout=1)
            break
        except Exception:
            time.sleep(0.05)
    # 清空共享测试库（幂等），保证用例间"首次连接无会话"等前提成立
    import os
    import sqlite3

    db_path = os.environ.get("CHAT_DB_PATH")
    if db_path:
        conn = sqlite3.connect(db_path)
        try:
            for table in ("messages", "conversations", "users", "models", "providers"):
                conn.execute(f"DELETE FROM {table}")
            conn.commit()
        finally:
            conn.close()
    yield base
    srv.should_exit = True
    thread.join(timeout=5)


def register(base: str, user_id: str, name: str):
    resp = httpx.post(f"{base}/api/users", json={"user_id": user_id, "display_name": name})
    assert resp.status_code == 201, resp.text


def ws_url(base: str, user_id: str) -> str:
    return f"ws://127.0.0.1:{base.split(':')[2]}/api/ws?user_id={user_id}"


def test_ws_ping_pong(server):
    register(server, "pinger", "P")
    with ws_connect(ws_url(server, "pinger")) as ws:
        assert json.loads(ws.recv()) == {"type": "sync"}
        ws.send(json.dumps({"type": "ping"}))
        assert json.loads(ws.recv()) == {"type": "pong"}


def test_ws_message_forward_and_ack(server):
    register(server, "a1", "A")
    register(server, "b1", "B")
    with ws_connect(ws_url(server, "a1")) as ws_a, ws_connect(ws_url(server, "b1")) as ws_b:
        assert json.loads(ws_a.recv()) == {"type": "sync"}
        assert json.loads(ws_b.recv()) == {"type": "sync"}
        ws_a.send(json.dumps({"type": "message", "to": "b1", "content": "你好"}))
        data = json.loads(ws_b.recv())
        assert data["type"] == "message"
        assert data["from"] == "a1"
        assert data["content"] == "你好"
        ack = json.loads(ws_a.recv())
        assert ack["type"] == "ack"
        assert ack["delivered"] is True


def test_ws_message_offline_ack_and_persisted(server):
    register(server, "a2", "A")
    register(server, "b2", "B")
    with ws_connect(ws_url(server, "a2")) as ws_a:
        assert json.loads(ws_a.recv()) == {"type": "sync"}
        ws_a.send(json.dumps({"type": "message", "to": "b2", "content": "离线消息"}))
        ack = json.loads(ws_a.recv())
        assert ack["type"] == "ack"
        assert ack["delivered"] is False  # b2 不在线，已落库待补推

    convs = httpx.get(f"{server}/api/conversations", headers={"X-User-Id": "a2"}).json()
    human = [c for c in convs if c["kind"] == "human" and c["peer_user_id"] == "b2"]
    assert len(human) == 1
    msgs = httpx.get(f"{server}/api/conversations/{human[0]['id']}/messages").json()
    assert msgs[-1]["content"] == "离线消息"
    assert msgs[-1]["sender_user_id"] == "a2"


def test_ws_unknown_user(server):
    register(server, "a3", "A")
    with ws_connect(ws_url(server, "a3")) as ws:
        assert json.loads(ws.recv()) == {"type": "sync"}
        ws.send(json.dumps({"type": "message", "to": "nobody", "content": "hi"}))
        data = json.loads(ws.recv())
        assert data["type"] == "error"
        assert data["code"] == "not_found"


def test_ws_typing_and_read_forward(server):
    register(server, "a4", "A")
    register(server, "b4", "B")
    with ws_connect(ws_url(server, "a4")) as ws_a, ws_connect(ws_url(server, "b4")) as ws_b:
        assert json.loads(ws_a.recv()) == {"type": "sync"}
        assert json.loads(ws_b.recv()) == {"type": "sync"}
        ws_a.send(json.dumps({"type": "typing", "to": "b4"}))
        assert json.loads(ws_b.recv()) == {"type": "typing", "from": "a4"}
        ws_a.send(json.dumps({"type": "read", "to": "b4"}))
        assert json.loads(ws_b.recv()) == {"type": "read", "from": "a4"}


def test_ws_online_notify(server):
    register(server, "a5", "A")
    register(server, "b5", "B")
    with ws_connect(ws_url(server, "b5")) as ws_b:
        assert json.loads(ws_b.recv()) == {"type": "sync"}
        with ws_connect(ws_url(server, "a5")) as ws_a:
            assert json.loads(ws_a.recv()) == {"type": "sync"}
            # 首次连接尚无会话，b5 不会收到 online 事件；发消息建立会话
            ws_a.send(json.dumps({"type": "message", "to": "b5", "content": "hi"}))
            assert json.loads(ws_b.recv())["type"] == "message"
            assert json.loads(ws_a.recv())["type"] == "ack"
        # a5 断开（b5 仍在线，已有会话）→ b5 收到 online=false
        assert json.loads(ws_b.recv()) == {"type": "online", "user_id": "a5", "online": False}
        # a5 重连 → b5 收到 online=true
        with ws_connect(ws_url(server, "a5")) as ws_a2:
            assert json.loads(ws_a2.recv()) == {"type": "sync"}
            assert json.loads(ws_b.recv()) == {"type": "online", "user_id": "a5", "online": True}


def test_ws_bad_request(server):
    register(server, "a6", "A")
    with ws_connect(ws_url(server, "a6")) as ws:
        assert json.loads(ws.recv()) == {"type": "sync"}
        ws.send(json.dumps({"type": "hack"}))
        data = json.loads(ws.recv())
        assert data["code"] == "bad_request"


def test_ws_multi_device_delivery(server):
    register(server, "a7", "A")
    register(server, "b7", "B")
    with (
        ws_connect(ws_url(server, "a7")) as ws_a,
        ws_connect(ws_url(server, "b7")) as ws_b1,
        ws_connect(ws_url(server, "b7")) as ws_b2,
    ):
        assert json.loads(ws_a.recv()) == {"type": "sync"}
        assert json.loads(ws_b1.recv()) == {"type": "sync"}
        assert json.loads(ws_b2.recv()) == {"type": "sync"}
        ws_a.send(json.dumps({"type": "message", "to": "b7", "content": "多端"}))
        assert json.loads(ws_b1.recv())["content"] == "多端"
        assert json.loads(ws_b2.recv())["content"] == "多端"
        assert json.loads(ws_a.recv())["type"] == "ack"


def test_ws_unregistered_identity_rejected(server):
    import websockets.exceptions

    with pytest.raises(
        (websockets.exceptions.ConnectionClosed, websockets.exceptions.InvalidStatus)
    ):
        with ws_connect(ws_url(server, "ghost")) as ws:
            ws.recv()
