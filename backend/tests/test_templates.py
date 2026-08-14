from app.providers.templates import TEMPLATE_BY_KIND, TEMPLATES


def test_templates_unique_kinds():
    kinds = [t.kind for t in TEMPLATES]
    assert len(kinds) == len(set(kinds))
    for required in (
        "deepseek",
        "doubao",
        "openai",
        "moonshot",
        "zhipu",
        "qwen",
        "ollama",
        "hermes",
        "dshweb",
        "custom",
    ):
        assert required in TEMPLATE_BY_KIND


def test_templates_fields():
    for t in TEMPLATES:
        assert t.kind and t.name
        if t.base_url:
            assert t.base_url.startswith("http")
        for m in t.default_models:
            assert m.model_id and m.display_name


def test_agent_templates():
    """agent 类模板：Hermes / dshweb，base_url 留空由用户填写，标记 agent=True。"""
    for kind in ("hermes", "dshweb"):
        t = TEMPLATE_BY_KIND[kind]
        assert t.agent is True
        assert t.base_url == ""
        assert t.default_models == ()
    for kind in ("deepseek", "openai", "custom"):
        assert TEMPLATE_BY_KIND[kind].agent is False
