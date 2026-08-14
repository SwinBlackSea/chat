from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..db import get_session
from ..models import Conversation, Model, Provider
from ..providers.dshweb_adapter import test_dshweb_connection
from ..providers.openai_compat import test_connection
from ..providers.templates import TEMPLATE_BY_KIND, TEMPLATES
from ..schemas import (
    ModelCreate,
    ModelOut,
    ModelPatch,
    ProviderCreate,
    ProviderOut,
    ProviderUpdate,
)
from ..utils import mask_key, normalize_base_url

router = APIRouter(prefix="/api")


def provider_out(provider: Provider) -> ProviderOut:
    return ProviderOut(
        id=provider.id,
        name=provider.name,
        kind=provider.kind,
        base_url=provider.base_url,
        api_key_masked=mask_key(provider.api_key),
        is_enabled=provider.is_enabled,
        model_count=len(provider.models),
    )


def model_out(model: Model, provider_name: str | None = None) -> ModelOut:
    return ModelOut(
        id=model.id,
        provider_id=model.provider_id,
        provider_name=provider_name or model.provider.name,
        model_id=model.model_id,
        display_name=model.display_name,
        avatar_color=model.avatar_color,
        context_length=model.context_length,
        is_enabled=model.is_enabled,
    )


async def get_provider_or_404(session: AsyncSession, provider_id: int) -> Provider:
    stmt = (
        select(Provider)
        .where(Provider.id == provider_id)
        .options(
            selectinload(Provider.models)
            .selectinload(Model.conversations)
            .selectinload(Conversation.messages)
        )
        .execution_options(populate_existing=True)
    )
    provider = (await session.execute(stmt)).scalar_one_or_none()
    if provider is None:
        raise HTTPException(status_code=404, detail="服务商不存在")
    return provider


@router.get("/providers/templates")
async def list_templates():
    return [
        {
            "kind": t.kind,
            "name": t.name,
            "base_url": t.base_url,
            "note": t.note,
            "default_models": [
                {
                    "model_id": m.model_id,
                    "display_name": m.display_name,
                    "context_length": m.context_length,
                }
                for m in t.default_models
            ],
        }
        for t in TEMPLATES
    ]


@router.get("/providers")
async def list_providers(session: AsyncSession = Depends(get_session)):
    providers = (
        (
            await session.execute(
                select(Provider).options(selectinload(Provider.models)).order_by(Provider.id)
            )
        )
        .scalars()
        .all()
    )
    return [provider_out(p) for p in providers]


@router.post("/providers", status_code=201)
async def create_provider(body: ProviderCreate, session: AsyncSession = Depends(get_session)):
    template = TEMPLATE_BY_KIND.get(body.kind)
    if template is None:
        raise HTTPException(status_code=400, detail=f"未知服务商类型：{body.kind}")
    try:
        base_url = normalize_base_url(body.base_url or template.base_url)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    name = (body.name or template.name).strip()
    if not name:
        raise HTTPException(status_code=400, detail="名称不能为空")
    provider = Provider(name=name, kind=body.kind, base_url=base_url, api_key=body.api_key)
    session.add(provider)
    await session.flush()

    if body.models is not None:
        defaults = [
            (m.model_id, m.display_name, m.context_length, m.avatar_color) for m in body.models
        ]
    else:
        defaults = [
            (m.model_id, m.display_name, m.context_length, None) for m in template.default_models
        ]
    for index, (model_id, display_name, context_length, avatar_color) in enumerate(defaults):
        session.add(
            Model(
                provider_id=provider.id,
                model_id=model_id,
                display_name=display_name or model_id,
                context_length=context_length,
                avatar_color=avatar_color,
                sort=index,
            )
        )
    await session.commit()
    provider = await get_provider_or_404(session, provider.id)
    return provider_out(provider)


@router.put("/providers/{provider_id}")
async def update_provider(
    provider_id: int, body: ProviderUpdate, session: AsyncSession = Depends(get_session)
):
    provider = await get_provider_or_404(session, provider_id)
    if body.name is not None:
        name = body.name.strip()
        if not name:
            raise HTTPException(status_code=400, detail="名称不能为空")
        provider.name = name
    if body.base_url is not None:
        try:
            provider.base_url = normalize_base_url(body.base_url)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
    if body.api_key is not None:
        provider.api_key = body.api_key
    if body.is_enabled is not None:
        provider.is_enabled = body.is_enabled
    await session.commit()
    return provider_out(provider)


@router.delete("/providers/{provider_id}", status_code=204)
async def delete_provider(provider_id: int, session: AsyncSession = Depends(get_session)):
    provider = await get_provider_or_404(session, provider_id)
    await session.delete(provider)
    await session.commit()


class TestIn(BaseModel):
    model: str | None = None


@router.post("/providers/{provider_id}/test")
async def test_provider(
    provider_id: int,
    body: TestIn | None = None,
    session: AsyncSession = Depends(get_session),
):
    provider = await get_provider_or_404(session, provider_id)
    model = (body.model if body else None) or next(
        (m.model_id for m in provider.models if m.is_enabled), None
    )
    if provider.kind == "dshweb":
        ok, message = await test_dshweb_connection(provider.base_url, provider.api_key)
    else:
        # Hermes 及其他 OpenAI 兼容服务商：最小 chat/completions 或 /models 请求
        ok, message = await test_connection(provider.base_url, provider.api_key, model)
    return {"ok": ok, "message": message, "model": model}


@router.get("/providers/{provider_id}/models")
async def list_models(provider_id: int, session: AsyncSession = Depends(get_session)):
    provider = await get_provider_or_404(session, provider_id)
    return [model_out(m, provider.name) for m in provider.models]


@router.post("/providers/{provider_id}/models", status_code=201)
async def add_model(
    provider_id: int, body: ModelCreate, session: AsyncSession = Depends(get_session)
):
    provider = await get_provider_or_404(session, provider_id)
    if any(m.model_id == body.model_id for m in provider.models):
        raise HTTPException(status_code=409, detail=f"模型已存在：{body.model_id}")
    model = Model(
        provider_id=provider.id,
        model_id=body.model_id,
        display_name=body.display_name or body.model_id,
        context_length=body.context_length,
        avatar_color=body.avatar_color,
        sort=len(provider.models),
    )
    session.add(model)
    await session.commit()
    return model_out(model, provider.name)


@router.get("/models")
async def list_enabled_models(session: AsyncSession = Depends(get_session)):
    rows = (
        (
            await session.execute(
                select(Model)
                .join(Provider)
                .where(Model.is_enabled.is_(True), Provider.is_enabled.is_(True))
                .options(selectinload(Model.provider))
                .order_by(Provider.id, Model.sort, Model.id)
            )
        )
        .scalars()
        .all()
    )
    return [model_out(m) for m in rows]


@router.patch("/models/{model_id}")
async def patch_model(
    model_id: int, body: ModelPatch, session: AsyncSession = Depends(get_session)
):
    model = await session.get(Model, model_id, options=[selectinload(Model.provider)])
    if model is None:
        raise HTTPException(status_code=404, detail="模型不存在")
    if body.is_enabled is not None:
        model.is_enabled = body.is_enabled
    if body.display_name is not None:
        display_name = body.display_name.strip()
        if not display_name:
            raise HTTPException(status_code=400, detail="显示名不能为空")
        model.display_name = display_name
    if "context_length" in body.model_fields_set:
        model.context_length = body.context_length
    if "avatar_color" in body.model_fields_set:
        model.avatar_color = body.avatar_color
    await session.commit()
    return model_out(model)


@router.delete("/models/{model_id}", status_code=204)
async def delete_model(model_id: int, session: AsyncSession = Depends(get_session)):
    stmt = (
        select(Model)
        .where(Model.id == model_id)
        .options(selectinload(Model.conversations).selectinload(Conversation.messages))
        .execution_options(populate_existing=True)
    )
    model = (await session.execute(stmt)).scalar_one_or_none()
    if model is None:
        raise HTTPException(status_code=404, detail="模型不存在")
    await session.delete(model)
    await session.commit()
