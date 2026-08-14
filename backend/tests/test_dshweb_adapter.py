import json

import httpx
import respx

from app.providers import ChatRequest, Done, DshwebAdapter, ProviderError, TokenDelta
from app.providers import dshweb_adapter as mod

BASE = "http://dsh.test"


def make_request(**overrides) -> ChatRequest:
    params = dict(
        base_url=BASE,
        api_key="",
        model="m",
        messages=[{"role": "user", "content": "你好"}],
        timeout_first_token=5,
        timeout_total=10,
    )
    params.update(overrides)
    return ChatRequest(**params)


def rpc_ok(value: dict) -> dict:
    return {"type": "server-response", "rpcId": "echo", "result": {"ok": True, "value": value}}


def rpc_error(code: str, message: str) -> dict:
    return {
        "type": "server-response",
        "rpcId": "echo",
        "result": {"ok": False, "error": {"code": code, "message": message}},
    }


def echo_rpc(value) -> httpx.Response:
    """构造带 rpcId 回显的成功响应。"""

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "type": "server-response",
                "rpcId": body["rpcId"],
                "result": {"ok": True, "value": value},
            },
        )

    return handler


def rpc_error_echo(code: str, message: str) -> httpx.Response:
    """构造带 rpcId 回显的失败响应。"""

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "type": "server-response",
                "rpcId": body["rpcId"],
                "result": {"ok": False, "error": {"code": code, "message": message}},
            },
        )

    return handler


def history_value(text: str) -> dict:
    return {
        "events": [
            {
                "event": {
                    "type": "assistant/message",
                    "seq": 2,
                    "time": 1,
                    "data": {"message": {"content": [{"type": "text", "text": text}]}},
                },
                "view": {},
            }
        ],
        "hasMore": False,
    }


async def collect(aiter):
    return [e async for e in aiter]


@respx.mock
async def test_chat_stream_full_cycle():
    respx.post(f"{BASE}/api/session.create").mock(side_effect=echo_rpc({"sessionId": "s-1"}))
    respx.post(f"{BASE}/api/session.prompt").mock(side_effect=echo_rpc({"accepted": True}))
    respx.post(f"{BASE}/api/session.list").mock(
        side_effect=echo_rpc({"items": [{"sessionId": "s-1", "running": False}]})
    )
    respx.post(f"{BASE}/api/session.history").mock(side_effect=echo_rpc(history_value("你好呀")))

    events = await collect(DshwebAdapter().chat_stream(make_request()))
    text = "".join(e.delta for e in events if isinstance(e, TokenDelta))
    assert text == "你好呀"
    assert isinstance(events[-1], Done)


@respx.mock
async def test_polls_until_running_false(monkeypatch):
    monkeypatch.setattr(mod, "POLL_INTERVAL_SECONDS", 0.01)
    calls = {"n": 0}

    def list_handler(request: httpx.Request) -> httpx.Response:
        calls["n"] += 1
        running = calls["n"] < 3
        body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "type": "server-response",
                "rpcId": body["rpcId"],
                "result": {
                    "ok": True,
                    "value": {"items": [{"sessionId": "s-1", "running": running}]},
                },
            },
        )

    respx.post(f"{BASE}/api/session.create").mock(side_effect=echo_rpc({"sessionId": "s-1"}))
    respx.post(f"{BASE}/api/session.prompt").mock(side_effect=echo_rpc({"accepted": True}))
    respx.post(f"{BASE}/api/session.list").mock(side_effect=list_handler)
    respx.post(f"{BASE}/api/session.history").mock(side_effect=echo_rpc(history_value("完成")))

    events = await collect(DshwebAdapter().chat_stream(make_request(timeout_total=5)))
    assert calls["n"] == 3
    text = "".join(e.delta for e in events if isinstance(e, TokenDelta))
    assert text == "完成"


@respx.mock
async def test_poll_timeout(monkeypatch):
    monkeypatch.setattr(mod, "POLL_INTERVAL_SECONDS", 0.01)
    respx.post(f"{BASE}/api/session.create").mock(side_effect=echo_rpc({"sessionId": "s-1"}))
    respx.post(f"{BASE}/api/session.prompt").mock(side_effect=echo_rpc({"accepted": True}))
    respx.post(f"{BASE}/api/session.list").mock(
        side_effect=echo_rpc({"items": [{"sessionId": "s-1", "running": True}]})
    )

    events = await collect(DshwebAdapter().chat_stream(make_request(timeout_total=0.1)))
    assert len(events) == 1
    assert isinstance(events[0], ProviderError)
    assert events[0].code == "timeout"


@respx.mock
async def test_bad_key():
    respx.post(f"{BASE}/api/session.create").mock(return_value=httpx.Response(401, json={}))
    events = await collect(DshwebAdapter().chat_stream(make_request()))
    assert isinstance(events[0], ProviderError)
    assert events[0].code == "bad_key"


@respx.mock
async def test_rpc_error_surface():
    respx.post(f"{BASE}/api/session.create").mock(side_effect=echo_rpc({"sessionId": "s-1"}))
    respx.post(f"{BASE}/api/session.prompt").mock(
        side_effect=rpc_error_echo("bad-request", "参数错误")
    )
    events = await collect(DshwebAdapter().chat_stream(make_request()))
    assert isinstance(events[0], ProviderError)
    assert events[0].code == "upstream_error"
    assert "参数错误" in events[0].message


@respx.mock
async def test_no_assistant_reply():
    respx.post(f"{BASE}/api/session.create").mock(side_effect=echo_rpc({"sessionId": "s-1"}))
    respx.post(f"{BASE}/api/session.prompt").mock(side_effect=echo_rpc({"accepted": True}))
    respx.post(f"{BASE}/api/session.list").mock(
        side_effect=echo_rpc({"items": [{"sessionId": "s-1", "running": False}]})
    )
    respx.post(f"{BASE}/api/session.history").mock(
        side_effect=echo_rpc({"events": [], "hasMore": False})
    )

    events = await collect(DshwebAdapter().chat_stream(make_request()))
    assert isinstance(events[0], ProviderError)
    assert events[0].code == "upstream_error"


@respx.mock
async def test_connection_test_ok():
    respx.post(f"{BASE}/api/session.list").mock(side_effect=echo_rpc({"items": []}))
    ok, message = await mod.test_dshweb_connection(BASE, "")
    assert ok is True
    assert message == "连接成功"


@respx.mock
async def test_connection_test_bad_key():
    respx.post(f"{BASE}/api/session.list").mock(return_value=httpx.Response(403, json={}))
    ok, message = await mod.test_dshweb_connection(BASE, "sk-x")
    assert ok is False
    assert "API Key" in message
