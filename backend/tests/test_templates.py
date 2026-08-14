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
        "custom",
    ):
        assert required in TEMPLATE_BY_KIND


def test_templates_fields():
    for t in TEMPLATES:
        assert t.kind and t.name
        if t.kind != "custom":
            assert t.base_url.startswith("http")
        for m in t.default_models:
            assert m.model_id and m.display_name
