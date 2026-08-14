"""dshweb（DeepSeek Harness）适配器。

协议（DSH host apiproxy v1 wire，见 @deepseek-ai/dsh 源码）：
- 所有 RPC 均为 POST /api/<method>，请求体为信封：
    {"type": "client-request", "rpcId": "<uuid>", "method": "<method>", "payload": {...}}
- 响应信封：
    {"type": "server-response", "rpcId": "<回显>", "result": {"ok": true, "value": {...}}}
    或 {"result": {"ok": false, "error": {"code": "...", "message": "..."}}}
- 服务端默认无鉴权；配置了 api_key 时附加 Authorization: Bearer。

聊天链路（每次请求独立会话，不跨请求复用）：
1. session.create 新建会话
2. session.prompt（mode=queue）提交完整上下文（system + 历史 + 新消息）
3. 轮询 session.list 直到该会话 running=false
4. session.history 取最后一条 assistant/message 的文本作为最终回复，按小块 yield 模拟流式
"""

import asyncio
import json
import time
import uuid

import httpx

from .base import (
    BAD_KEY,
    NETWORK_ERROR,
    RATE_LIMIT,
    TIMEOUT,
    UPSTREAM_ERROR,
    ChatRequest,
    Done,
    ProviderError,
    TokenDelta,
)

POLL_INTERVAL_SECONDS = 1.0
RESPONSE_CHUNK_CHARS = 64

# dshweb RPC 错误码 → ChatHub 统一错误码
_RPC_CODE_MAP = {
    "unauthorized": BAD_KEY,
    "forbidden": BAD_KEY,
    "rate-limited": RATE_LIMIT,
    "timeout": TIMEOUT,
}


class RpcError(Exception):
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(message)


def _join_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def _headers(api_key: str) -> dict[str, str]:
    headers = {"content-type": "application/json"}
    if api_key:
        headers["authorization"] = f"Bearer {api_key}"
    return headers


def _render_messages(messages: list[dict]) -> str:
    """把 ChatHub 上下文（system/user/assistant）渲染成 dshweb 单条 prompt 文本。"""
    labels = {"system": "[系统]", "user": "[用户]", "assistant": "[助手]"}
    parts = [
        f"{labels.get(m.get('role'), '?')} {m['content']}".strip()
        for m in messages
        if m.get("content")
    ]
    return "\n\n".join(parts)


async def _rpc_call(
    client: httpx.AsyncClient,
    base_url: str,
    api_key: str,
    method: str,
    payload: dict,
    timeout: float,
) -> dict:
    """发送一条 RPC 信封，返回 result.value；失败抛 RpcError。"""
    rpc_id = str(uuid.uuid4())
    envelope = {"type": "client-request", "rpcId": rpc_id, "method": method, "payload": payload}
    try:
        resp = await client.post(
            _join_url(base_url, f"/api/{method}"),
            json=envelope,
            headers=_headers(api_key),
            timeout=timeout,
        )
    except httpx.TimeoutException as exc:
        raise RpcError(TIMEOUT, "请求超时") from exc
    except httpx.HTTPError as exc:
        raise RpcError(NETWORK_ERROR, f"网络错误：{exc.__class__.__name__}") from exc
    if resp.status_code in (401, 403):
        raise RpcError(BAD_KEY, "API Key 无效或无权访问")
    if resp.status_code >= 400:
        raise RpcError(UPSTREAM_ERROR, f"服务返回 HTTP {resp.status_code}")
    try:
        full = resp.json()
    except json.JSONDecodeError as exc:
        raise RpcError(UPSTREAM_ERROR, "响应不是合法 JSON") from exc
    if full.get("type") != "server-response" or full.get("rpcId") != rpc_id:
        raise RpcError(UPSTREAM_ERROR, "RPC 响应信封异常（rpcId 不匹配）")
    result = full.get("result") or {}
    if not result.get("ok"):
        error = result.get("error") or {}
        code = str(error.get("code") or UPSTREAM_ERROR)
        message = str(error.get("message") or "RPC 调用失败")
        raise RpcError(_RPC_CODE_MAP.get(code, UPSTREAM_ERROR), message)
    return result.get("value") or {}


def _last_assistant_text(history: dict) -> str | None:
    events = history.get("events") or []
    for entry in reversed(events):
        event = entry.get("event") or {}
        if event.get("type") != "assistant/message":
            continue
        data = event.get("data") or {}
        message = data.get("message") or {}
        content = message.get("content") or []
        texts = [
            block.get("text", "")
            for block in content
            if isinstance(block, dict) and block.get("type") == "text"
        ]
        if texts:
            return "\n".join(texts).strip() or None
    return None


class DshwebAdapter:
    async def chat_stream(self, req: ChatRequest):
        try:
            async with httpx.AsyncClient() as client:
                created = await _rpc_call(
                    client, req.base_url, req.api_key, "session.create", {}, req.timeout_first_token
                )
                session_id = created.get("sessionId")
                if not session_id:
                    yield ProviderError(UPSTREAM_ERROR, "session.create 未返回 sessionId")
                    return

                prompt_text = _render_messages(req.messages)
                await _rpc_call(
                    client,
                    req.base_url,
                    req.api_key,
                    "session.prompt",
                    {
                        "sessionId": session_id,
                        "mode": "queue",
                        "content": [{"type": "text", "text": prompt_text}],
                    },
                    req.timeout_first_token,
                )

                started = time.monotonic()
                while True:
                    value = await _rpc_call(
                        client,
                        req.base_url,
                        req.api_key,
                        "session.list",
                        {},
                        req.timeout_first_token,
                    )
                    row = next(
                        (
                            item
                            for item in value.get("items", [])
                            if item.get("sessionId") == session_id
                        ),
                        None,
                    )
                    if row is None or not row.get("running"):
                        break
                    if time.monotonic() - started > req.timeout_total:
                        yield ProviderError(TIMEOUT, "agent 任务执行超时，请稍后重试")
                        return
                    await asyncio.sleep(POLL_INTERVAL_SECONDS)

                history = await _rpc_call(
                    client,
                    req.base_url,
                    req.api_key,
                    "session.history",
                    {"sessionId": session_id},
                    req.timeout_first_token,
                )
                text = _last_assistant_text(history)
                if text is None:
                    yield ProviderError(UPSTREAM_ERROR, "未取到 agent 回复")
                    return
                for index in range(0, len(text), RESPONSE_CHUNK_CHARS):
                    yield TokenDelta(text[index : index + RESPONSE_CHUNK_CHARS])
                yield Done()
        except RpcError as exc:
            yield ProviderError(exc.code, exc.message)
            return
        except (asyncio.TimeoutError, httpx.TimeoutException):
            yield ProviderError(TIMEOUT, "等待 agent 响应超时")
            return


async def test_dshweb_connection(base_url: str, api_key: str) -> tuple[bool, str]:
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(10.0)) as client:
            await _rpc_call(client, base_url, api_key, "session.list", {}, 10.0)
        return True, "连接成功"
    except RpcError as exc:
        return False, exc.message
