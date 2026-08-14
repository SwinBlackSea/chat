from dataclasses import dataclass


@dataclass(frozen=True)
class DefaultModel:
    model_id: str
    display_name: str
    context_length: int | None = None


@dataclass(frozen=True)
class ProviderTemplate:
    kind: str
    name: str
    base_url: str
    note: str = ""
    default_models: tuple[DefaultModel, ...] = ()


TEMPLATES: list[ProviderTemplate] = [
    ProviderTemplate(
        "deepseek",
        "DeepSeek",
        "https://api.deepseek.com",
        "深度求索",
        (
            DefaultModel("deepseek-chat", "DeepSeek 对话", 64000),
            DefaultModel("deepseek-reasoner", "DeepSeek 推理", 64000),
        ),
    ),
    ProviderTemplate(
        "doubao",
        "豆包（火山方舟）",
        "https://ark.cn-beijing.volces.com/api/v3",
        "模型名支持接入点 ID（ep-xxx）或模型名",
    ),
    ProviderTemplate(
        "openai",
        "OpenAI",
        "https://api.openai.com/v1",
        "官方接口",
        (
            DefaultModel("gpt-4o-mini", "GPT-4o mini", 128000),
            DefaultModel("gpt-4o", "GPT-4o", 128000),
        ),
    ),
    ProviderTemplate(
        "moonshot",
        "Moonshot",
        "https://api.moonshot.cn/v1",
        "Kimi 系列",
        (
            DefaultModel("moonshot-v1-8k", "Kimi 8K", 8000),
            DefaultModel("moonshot-v1-32k", "Kimi 32K", 32000),
        ),
    ),
    ProviderTemplate(
        "zhipu",
        "智谱 GLM",
        "https://open.bigmodel.cn/api/paas/v4",
        "GLM 系列",
        (
            DefaultModel("glm-4-flash", "GLM-4 Flash", 128000),
            DefaultModel("glm-4-plus", "GLM-4 Plus", 128000),
        ),
    ),
    ProviderTemplate(
        "qwen",
        "通义千问",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "Qwen 系列",
        (
            DefaultModel("qwen-plus", "Qwen Plus", 131072),
            DefaultModel("qwen-turbo", "Qwen Turbo", 131072),
        ),
    ),
    ProviderTemplate("ollama", "Ollama（本地）", "http://localhost:11434/v1", "本地模型，无需 Key"),
    ProviderTemplate("custom", "自定义", "", "任意 OpenAI 兼容端点"),
]
TEMPLATE_BY_KIND: dict[str, ProviderTemplate] = {t.kind: t for t in TEMPLATES}
