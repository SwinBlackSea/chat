import httpx
import respx

DEEPSEEK = {"kind": "deepseek", "api_key": "sk-1234567890abcdef"}
UPSTREAM = "https://api.deepseek.com/chat/completions"

SSE_REPLY = (
    'data: {"choices":[{"delta":{"content":"你好"}}]}\n\n'
    'data: {"choices":[{}],"usage":{"prompt_tokens":1,"completion_tokens":1}}\n\n'
    "data: [DONE]\n\n"
)


async def seed_provider(client) -> dict:
    resp = await client.post("/api/providers", json=DEEPSEEK)
    assert resp.status_code == 201
    return resp.json()


async def test_health(client):
    resp = await client.get("/api/health")
    assert resp.json() == {"status": "ok"}


async def test_templates_endpoint(client):
    resp = await client.get("/api/providers/templates")
    kinds = {t["kind"] for t in resp.json()}
    assert {"deepseek", "doubao", "custom"} <= kinds


async def test_provider_create_masks_key_and_seeds_models(client):
    data = await seed_provider(client)
    assert data["api_key_masked"] == "sk-1****cdef"
    assert "1234567890" not in str(data)
    assert data["model_count"] == 2

    models = (await client.get(f"/api/providers/{data['id']}/models")).json()
    assert [m["model_id"] for m in models] == ["deepseek-chat", "deepseek-reasoner"]
    assert models[0]["display_name"] == "DeepSeek 对话"

    enabled = (await client.get("/api/models")).json()
    assert len(enabled) == 2

    bad = await client.post("/api/providers", json={"kind": "nope"})
    assert bad.status_code == 400


async def test_provider_update_and_delete_cascade(client):
    data = await seed_provider(client)
    resp = await client.put(
        f"/api/providers/{data['id']}", json={"name": "我的 DS", "is_enabled": False}
    )
    assert resp.json()["name"] == "我的 DS"
    assert resp.json()["is_enabled"] is False
    assert (await client.get("/api/models")).json() == []

    resp = await client.delete(f"/api/providers/{data['id']}")
    assert resp.status_code == 204
    assert (await client.get("/api/providers")).json() == []


async def test_conversation_idempotent(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    conv1 = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()
    conv2 = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()
    assert conv1["id"] == conv2["id"]
    assert conv1["contact_name"] == "DeepSeek 对话"
    assert conv1["provider_name"] == "DeepSeek"

    resp = await client.post("/api/conversations", json={"model_id": 9999})
    assert resp.status_code == 404


async def test_chat_sse_full_cycle(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    conv = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(return_value=httpx.Response(200, text=SSE_REPLY))
        resp = await client.post(
            "/api/chat", json={"conversation_id": conv["id"], "content": "在吗"}
        )
    assert resp.status_code == 200
    assert "event: token" in resp.text
    assert "event: done" in resp.text

    messages = (await client.get(f"/api/conversations/{conv['id']}/messages")).json()
    assert [m["role"] for m in messages] == ["user", "assistant"]
    assert messages[1]["content"] == "你好"

    convs = (await client.get("/api/conversations")).json()
    assert convs[0]["id"] == conv["id"]
    assert convs[0]["last_message_preview"] == "你好"

    resp = await client.delete(f"/api/conversations/{conv['id']}/messages")
    assert resp.status_code == 204
    assert (await client.get(f"/api/conversations/{conv['id']}/messages")).json() == []


async def test_chat_upstream_error_persisted(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    conv = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(
            return_value=httpx.Response(401, json={"error": {"message": "bad key"}})
        )
        resp = await client.post(
            "/api/chat", json={"conversation_id": conv["id"], "content": "在吗"}
        )
    assert "event: error" in resp.text
    assert "bad_key" in resp.text

    messages = (await client.get(f"/api/conversations/{conv['id']}/messages")).json()
    assert messages[-1]["error"].startswith("bad_key")

    convs = (await client.get("/api/conversations")).json()
    assert convs[0]["last_message_preview"].startswith("[出错]")


async def test_connection_test_endpoint(client):
    provider = await seed_provider(client)
    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(return_value=httpx.Response(200, json={"choices": []}))
        resp = await client.post(f"/api/providers/{provider['id']}/test", json={})
    assert resp.json()["ok"] is True

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(return_value=httpx.Response(401, json={}))
        resp = await client.post(f"/api/providers/{provider['id']}/test", json={})
    assert resp.json()["ok"] is False


async def test_model_patch_and_delete(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    target = models[1]

    resp = await client.patch(f"/api/models/{target['id']}", json={"is_enabled": False})
    assert resp.json()["is_enabled"] is False
    assert len((await client.get("/api/models")).json()) == 1

    resp = await client.delete(f"/api/models/{target['id']}")
    assert resp.status_code == 204
    assert len((await client.get(f"/api/providers/{provider['id']}/models")).json()) == 1


async def test_chat_regenerate_replaces_last_assistant(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    conv = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(return_value=httpx.Response(200, text=SSE_REPLY))
        resp = await client.post(
            "/api/chat", json={"conversation_id": conv["id"], "content": "在吗"}
        )
    assert "event: done" in resp.text

    # 重新生成：上游返回不同内容，最后一条 assistant 被替换
    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(
            return_value=httpx.Response(
                200,
                text='data: {"choices":[{"delta":{"content":"重新回答"}}]}\n\n'
                'data: {"choices":[{}],"usage":{"prompt_tokens":1,"completion_tokens":1}}\n\n'
                "data: [DONE]\n\n",
            )
        )
        resp = await client.post(
            "/api/chat",
            json={"conversation_id": conv["id"], "content": "在吗", "regenerate": True},
        )
    assert "event: done" in resp.text

    messages = (await client.get(f"/api/conversations/{conv['id']}/messages")).json()
    assert [m["role"] for m in messages] == ["user", "assistant"]
    assert messages[-1]["content"] == "重新回答"


async def test_chat_regenerate_mismatch_rejected(client):
    provider = await seed_provider(client)
    models = (await client.get(f"/api/providers/{provider['id']}/models")).json()
    conv = (await client.post("/api/conversations", json={"model_id": models[0]["id"]})).json()

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(UPSTREAM).mock(return_value=httpx.Response(200, text=SSE_REPLY))
        await client.post("/api/chat", json={"conversation_id": conv["id"], "content": "在吗"})

    resp = await client.post(
        "/api/chat",
        json={"conversation_id": conv["id"], "content": "内容已变", "regenerate": True},
    )
    assert resp.status_code == 409

    # 空会话直接 regenerate 也拒绝
    provider2 = (await client.post("/api/providers", json=DEEPSEEK)).json()
    models2 = (await client.get(f"/api/providers/{provider2['id']}/models")).json()
    conv2 = (await client.post("/api/conversations", json={"model_id": models2[0]["id"]})).json()
    resp = await client.post(
        "/api/chat",
        json={"conversation_id": conv2["id"], "content": "hi", "regenerate": True},
    )
    assert resp.status_code == 409


DSHWEB = {
    "kind": "dshweb",
    "name": "我的 dshweb",
    "base_url": "http://dsh.test",
    "api_key": "",
}
DSH = "http://dsh.test"


def dsh_envelope(value: dict):
    """构造带 rpcId 回显的响应 handler（respx side_effect）。"""

    def handler(request: httpx.Request) -> httpx.Response:
        rpc_id = (
            request.content and __import__("json").loads(request.content).get("rpcId")
        ) or "echo"
        return httpx.Response(
            200,
            json={
                "type": "server-response",
                "rpcId": rpc_id,
                "result": {"ok": True, "value": value},
            },
        )

    return handler


async def seed_dshweb(client) -> dict:
    resp = await client.post("/api/providers", json=DSHWEB)
    assert resp.status_code == 201
    return resp.json()


async def test_dshweb_provider_create_and_test(client):
    provider = await seed_dshweb(client)
    assert provider["kind"] == "dshweb"
    assert provider["model_count"] == 0  # 模板无默认模型

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(f"{DSH}/api/session.list").mock(side_effect=dsh_envelope({"items": []}))
        resp = await client.post(f"/api/providers/{provider['id']}/test", json={})
    assert resp.json()["ok"] is True

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(f"{DSH}/api/session.list").mock(return_value=httpx.Response(401, json={}))
        resp = await client.post(f"/api/providers/{provider['id']}/test", json={})
    assert resp.json()["ok"] is False


async def test_dshweb_chat_full_cycle(client):
    provider = await seed_dshweb(client)
    model = (
        await client.post(
            f"/api/providers/{provider['id']}/models",
            json={"model_id": "agent", "display_name": "我的 Agent"},
        )
    ).json()
    conv = (await client.post("/api/conversations", json={"model_id": model["id"]})).json()

    async with respx.mock:
        respx.route(host="test").pass_through()
        respx.post(f"{DSH}/api/session.create").mock(side_effect=dsh_envelope({"sessionId": "s-1"}))
        respx.post(f"{DSH}/api/session.prompt").mock(side_effect=dsh_envelope({"accepted": True}))
        respx.post(f"{DSH}/api/session.list").mock(
            side_effect=dsh_envelope({"items": [{"sessionId": "s-1", "running": False}]})
        )
        respx.post(f"{DSH}/api/session.history").mock(
            side_effect=dsh_envelope(
                {
                    "events": [
                        {
                            "event": {
                                "type": "assistant/message",
                                "seq": 2,
                                "time": 1,
                                "data": {
                                    "message": {"content": [{"type": "text", "text": "agent 回复"}]}
                                },
                            },
                            "view": {},
                        }
                    ],
                    "hasMore": False,
                }
            )
        )
        resp = await client.post(
            "/api/chat", json={"conversation_id": conv["id"], "content": "帮我看下"}
        )

    assert resp.status_code == 200
    assert "event: token" in resp.text
    assert "agent 回复" in resp.text
    assert "event: done" in resp.text

    messages = (await client.get(f"/api/conversations/{conv['id']}/messages")).json()
    assert [m["role"] for m in messages] == ["user", "assistant"]
    assert messages[1]["content"] == "agent 回复"
