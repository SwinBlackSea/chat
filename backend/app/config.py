import os


class Settings:
    def __init__(self) -> None:
        self.db_path: str = os.environ.get("CHAT_DB_PATH", "data/chat.db")
        self.timeout_first_token: float = float(os.environ.get("CHAT_TIMEOUT_FIRST_TOKEN", "60"))
        self.timeout_total: float = float(os.environ.get("CHAT_TIMEOUT_TOTAL", "300"))
        # agent 类服务商（Hermes / dshweb）：多步工具调用耗时长，超时放宽
        self.timeout_agent_first_token: float = float(
            os.environ.get("CHAT_TIMEOUT_AGENT_FIRST_TOKEN", "300")
        )
        self.timeout_agent_total: float = float(os.environ.get("CHAT_TIMEOUT_AGENT_TOTAL", "3600"))
        self.connect_test_timeout: float = 10.0


settings = Settings()
