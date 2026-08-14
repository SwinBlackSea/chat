import os


class Settings:
    def __init__(self) -> None:
        self.db_path: str = os.environ.get("CHAT_DB_PATH", "data/chat.db")
        self.timeout_first_token: float = float(os.environ.get("CHAT_TIMEOUT_FIRST_TOKEN", "60"))
        self.timeout_total: float = float(os.environ.get("CHAT_TIMEOUT_TOTAL", "300"))
        self.connect_test_timeout: float = 10.0


settings = Settings()
