import httpx
import respx

from app.providers import (
    ChatRequest,
    Done,
    OpenAICompatAdapter,
    ProviderError,
    TokenDelta,
)

UPSTREAM = "https://up.test/v1/chat/completions"

SSE_OK = (
    'data: {"choices":[{"delta":{"role":"assistant","content":""}}]}\n\n'
    'data: {"choices":[{"delta":{"content":"你"}}]}\n\n'
    'data: {"choices":[{"delta":{"content":"好"}}]}\n\n'
    'data: {"choices":[{"delta":{}}],"usage":{"prompt_tokens":3,"completion_tokens":2}}\n\n'
    "data: [DONE]\n\n"
)


def make_request() -> ChatRequest:
    return ChatRequest(
        base_url="https://up.test/v1",
        api_key="sk-x",
        model="m",
        messages=[{"role": "user", "content": "hi"}],
        timeout_first_token=5,
        timeout_total=10,
    )


async def collect(aiter):
    return [e async for e in aiter]


@respx.mock
async def test_stream_ok():
    respx.post(UPSTREAM).mock(return_value=httpx.Response(200, text=SSE_OK))
    events = await collect(OpenAICompatAdapter().chat_stream(make_request()))
    deltas = [e.delta for e in events if isinstance(e, TokenDelta)]
    assert deltas == ["你", "好"]
    assert isinstance(events[-1], Done)
    assert events[-1].usage == {"prompt_tokens": 3, "completion_tokens": 2}


@respx.mock
async def test_bad_key():
    respx.post(UPSTREAM).mock(
        return_value=httpx.Response(401, json={"error": {"message": "invalid api key"}})
    )
    events = await collect(OpenAICompatAdapter().chat_stream(make_request()))
    assert len(events) == 1
    assert isinstance(events[0], ProviderError)
    assert events[0].code == "bad_key"


@respx.mock
async def test_model_not_found():
    respx.post(UPSTREAM).mock(
        return_value=httpx.Response(
            404, json={"error": {"message": "The model `x` does not exist"}}
        )
    )
    events = await collect(OpenAICompatAdapter().chat_stream(make_request()))
    assert events[0].code == "model_not_found"


@respx.mock
async def test_rate_limit():
    respx.post(UPSTREAM).mock(return_value=httpx.Response(429, text="too many requests"))
    events = await collect(OpenAICompatAdapter().chat_stream(make_request()))
    assert events[0].code == "rate_limit"


@respx.mock
async def test_split_chunks_and_ignore_non_data_lines():
    body = (
        ": keep-alive\n\n"
        'data: {"choices":[{"delta":{"content":"A"}}]}\n\n'
        "event: ping\n"
        "not-sse garbage\n"
        'data: {"choices":[{"delta":{"content":"B"}}]}\n\n'
        "data: [DONE]\n\n"
    )
    respx.post(UPSTREAM).mock(return_value=httpx.Response(200, text=body))
    events = await collect(OpenAICompatAdapter().chat_stream(make_request()))
    deltas = [e.delta for e in events if isinstance(e, TokenDelta)]
    assert deltas == ["A", "B"]
    assert isinstance(events[-1], Done)
