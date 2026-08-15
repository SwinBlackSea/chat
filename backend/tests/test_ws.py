"""WebSocket 实时通道测试（独立于 AI SSE 链路；不走 lifespan，不依赖数据库）。"""

from fastapi.testclient import TestClient

from app.main import app
from app.services.ws import manager

client = TestClient(app)


def test_ws_ping_pong():
    with client.websocket_connect("/api/ws?client_id=a") as ws:
        ws.send_json({"type": "ping"})
        assert ws.receive_json() == {"type": "pong"}


def test_ws_message_forward():
    with (
        client.websocket_connect("/api/ws?client_id=a") as ws_a,
        client.websocket_connect("/api/ws?client_id=b") as ws_b,
    ):
        ws_a.send_json({"type": "message", "to": "b", "content": "你好"})
        assert ws_b.receive_json() == {"type": "message", "from": "a", "content": "你好"}


def test_ws_offline_target():
    with client.websocket_connect("/api/ws?client_id=a") as ws:
        ws.send_json({"type": "message", "to": "nobody", "content": "hi"})
        data = ws.receive_json()
        assert data["type"] == "error"
        assert data["code"] == "offline"


def test_ws_unknown_type():
    with client.websocket_connect("/api/ws?client_id=a") as ws:
        ws.send_json({"type": "hack"})
        data = ws.receive_json()
        assert data["type"] == "error"
        assert data["code"] == "bad_request"


def test_ws_missing_fields():
    with client.websocket_connect("/api/ws?client_id=a") as ws:
        ws.send_json({"type": "message", "to": "b"})  # 缺 content
        data = ws.receive_json()
        assert data["code"] == "bad_request"


def test_ws_disconnect_cleans_up():
    conn = client.websocket_connect("/api/ws?client_id=temp")
    conn.__enter__()
    assert manager.is_online("temp")
    conn.__exit__(None, None, None)
    assert not manager.is_online("temp")


def test_ws_multi_device_delivery():
    """接收方多端在线时，所有连接都收到。"""
    with (
        client.websocket_connect("/api/ws?client_id=a") as ws_a,
        client.websocket_connect("/api/ws?client_id=b") as ws_b1,
        client.websocket_connect("/api/ws?client_id=b") as ws_b2,
    ):
        ws_a.send_json({"type": "message", "to": "b", "content": "多端"})
        assert ws_b1.receive_json() == {"type": "message", "from": "a", "content": "多端"}
        assert ws_b2.receive_json() == {"type": "message", "from": "a", "content": "多端"}
