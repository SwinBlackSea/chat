import asyncio
import json
import re

import httpx

from .base import (
    BAD_KEY,
    INSUFFICIENT_BALANCE,
    MODEL_NOT_FOUND,
    NETWORK_ERROR,
    RATE_LIMIT,
    TIMEOUT,
    UPSTREAM_ERROR,
    ChatRequest,
    Done,
    ProviderError,
    TokenDelta,
)


def _join_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def _redact_secrets(message: str, secrets: tuple[str, ...]) -> str:
    safe = message
    for secret in secrets:
        if secret:
            safe = safe.replace(secret, "[已脱敏]")
    safe = re.sub(r"(?i)(api[_ -]?key\s*[:=]\s*)\S+", r"\1[已脱敏]", safe)
    safe = re.sub(r"(?i)(bearer\s+)[A-Za-z0-9._~+/=-]+", r"\1[已脱敏]", safe)
    return safe


def map_upstream_error(
    status_code: int,
    body_text: str,
    secrets: tuple[str, ...] = (),
) -> ProviderError:
    message = (body_text or "").strip()[:300] or f"HTTP {status_code}"
    try:
        data = json.loads(body_text)
        err = data.get("error")
        if isinstance(err, dict) and err.get("message"):
            message = str(err["message"])[:300]
    except (json.JSONDecodeError, AttributeError, TypeError):
        pass
    message = _redact_secrets(message, secrets)
    lowered = message.lower()
    if status_code in (401, 403):
        return ProviderError(BAD_KEY, "API Key 无效或无权访问")
    if status_code == 402 or "balance" in lowered or "insufficient" in lowered or "欠费" in message:
        return ProviderError(INSUFFICIENT_BALANCE, f"余额不足：{message}")
    if status_code == 429:
        return ProviderError(RATE_LIMIT, "请求过于频繁，请稍后再试")
    if status_code == 404 and "model" in lowered:
        return ProviderError(MODEL_NOT_FOUND, f"模型不存在：{message}")
    return ProviderError(UPSTREAM_ERROR, f"上游错误（HTTP {status_code}）：{message}")


class OpenAICompatAdapter:
    async def chat_stream(self, req: ChatRequest):
        url = _join_url(req.base_url, "/chat/completions")
        headers = {"Accept": "text/event-stream"}
        if req.api_key:
            headers["Authorization"] = f"Bearer {req.api_key}"
        payload = {"model": req.model, "messages": req.messages, "stream": True}
        timeout = httpx.Timeout(10.0, read=req.timeout_first_token, write=10.0, pool=10.0)
        usage: dict | None = None
        try:
            async with asyncio.timeout(req.timeout_total):
                async with httpx.AsyncClient(timeout=timeout) as client:
                    async with client.stream("POST", url, json=payload, headers=headers) as resp:
                        if resp.status_code != 200:
                            body = (await resp.aread()).decode("utf-8", "replace")
                            yield map_upstream_error(resp.status_code, body, (req.api_key,))
                            return
                        async for line in resp.aiter_lines():
                            line = line.strip()
                            if not line or not line.startswith("data:"):
                                continue
                            data = line[len("data:") :].strip()
                            if data == "[DONE]":
                                break
                            try:
                                chunk = json.loads(data)
                            except json.JSONDecodeError:
                                continue
                            choices = chunk.get("choices") or []
                            if choices:
                                delta = (choices[0].get("delta") or {}).get("content")
                                if delta:
                                    yield TokenDelta(delta)
                            if chunk.get("usage"):
                                usage = chunk["usage"]
        except TimeoutError:
            yield ProviderError(TIMEOUT, "响应超时，请稍后重试")
            return
        except httpx.ReadTimeout:
            yield ProviderError(TIMEOUT, "等待上游响应超时")
            return
        except httpx.HTTPError as exc:
            yield ProviderError(NETWORK_ERROR, f"网络错误：{exc.__class__.__name__}")
            return
        yield Done(usage)


async def test_connection(base_url: str, api_key: str, model: str | None) -> tuple[bool, str]:
    headers = {}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(10.0)) as client:
            if model:
                payload = {
                    "model": model,
                    "messages": [{"role": "user", "content": "hi"}],
                    "max_tokens": 1,
                }
                resp = await client.post(
                    _join_url(base_url, "/chat/completions"),
                    json=payload,
                    headers=headers,
                )
            else:
                resp = await client.get(_join_url(base_url, "/models"), headers=headers)
    except httpx.HTTPError as exc:
        return False, f"连接失败：{exc.__class__.__name__}"
    if resp.status_code in (401, 403):
        return False, "API Key 无效或无权访问"
    if resp.status_code >= 400:
        return False, f"服务返回 HTTP {resp.status_code}"
    return True, "连接成功"
