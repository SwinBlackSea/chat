from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Protocol

BAD_KEY = "bad_key"
INSUFFICIENT_BALANCE = "insufficient_balance"
RATE_LIMIT = "rate_limit"
MODEL_NOT_FOUND = "model_not_found"
UPSTREAM_ERROR = "upstream_error"
NETWORK_ERROR = "network_error"
TIMEOUT = "timeout"


@dataclass(slots=True)
class ChatRequest:
    base_url: str
    api_key: str
    model: str
    messages: list[dict]
    timeout_first_token: float = 60.0
    timeout_total: float = 300.0


@dataclass(slots=True)
class TokenDelta:
    delta: str


@dataclass(slots=True)
class Done:
    usage: dict | None = None


@dataclass(slots=True)
class ProviderError:
    code: str
    message: str


StreamEvent = TokenDelta | Done | ProviderError


class ChatAdapter(Protocol):
    async def chat_stream(self, req: ChatRequest) -> AsyncIterator[StreamEvent]: ...
