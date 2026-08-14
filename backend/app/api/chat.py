import json

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_session
from ..schemas import ChatIn
from ..services.chat import prepare_chat, stream_chat

router = APIRouter(prefix="/api")


@router.post("/chat")
async def chat(body: ChatIn, session: AsyncSession = Depends(get_session)):
    prepared = await prepare_chat(
        session,
        body.conversation_id,
        body.content,
        regenerate=body.regenerate,
    )

    async def sse():
        async for event in stream_chat(session, prepared):
            payload = {k: v for k, v in event.items() if k != "type"}
            yield f"event: {event['type']}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        sse(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
