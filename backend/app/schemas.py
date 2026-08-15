from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ModelCreate(BaseModel):
    model_id: str = Field(min_length=1, max_length=128)
    display_name: str | None = Field(default=None, max_length=64)
    context_length: int | None = Field(default=None, gt=0)
    avatar_color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")

    @field_validator("model_id")
    @classmethod
    def strip_model_id(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("model_id 不能为空")
        return value


class UserCreate(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)
    display_name: str | None = Field(default=None, max_length=64)
    avatar_color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")

    @field_validator("user_id")
    @classmethod
    def strip_user_id(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("user_id 不能为空")
        return value


class UserOut(BaseModel):
    id: int
    user_id: str
    display_name: str
    avatar_color: str | None
    avatar: str | None = None  # 头像 URL 路径（/avatars/xxx.png）


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, max_length=64)
    avatar_color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")


class ProviderCreate(BaseModel):
    kind: str
    name: str | None = Field(default=None, max_length=64)
    base_url: str | None = Field(default=None, max_length=255)
    api_key: str = Field(default="", max_length=255)
    models: list[ModelCreate] | None = None


class ProviderUpdate(BaseModel):
    name: str | None = Field(default=None, max_length=64)
    base_url: str | None = Field(default=None, max_length=255)
    api_key: str | None = Field(default=None, max_length=255)
    is_enabled: bool | None = None


class ModelPatch(BaseModel):
    is_enabled: bool | None = None
    display_name: str | None = Field(default=None, max_length=64)
    context_length: int | None = Field(default=None, gt=0)
    avatar_color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")


class ProviderOut(BaseModel):
    id: int
    name: str
    kind: str
    base_url: str
    api_key_masked: str
    is_enabled: bool
    model_count: int


class ModelOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    provider_id: int
    provider_name: str = ""
    model_id: str
    display_name: str
    avatar_color: str | None
    context_length: int | None
    is_enabled: bool


class ConversationCreate(BaseModel):
    kind: str = "bot"
    model_id: int | None = None
    peer_user_id: str | None = None

    @field_validator("kind")
    @classmethod
    def valid_kind(cls, value: str) -> str:
        if value not in ("bot", "human"):
            raise ValueError("kind 必须是 bot 或 human")
        return value


class ConversationUpdate(BaseModel):
    system_prompt: str | None = None


class ConversationOut(BaseModel):
    id: int
    kind: str
    model_id: int | None = None
    contact_name: str
    model_code: str = ""
    provider_name: str = ""
    avatar_color: str | None = None
    avatar: str | None = None  # 联系人头像 URL 路径（human 会话对方、bot 会话无）
    context_length: int | None = None
    system_prompt: str | None = None
    peer_user_id: str | None = None
    last_message_preview: str | None = None
    last_message_time: datetime | None = None
    updated_at: datetime
    unread_count: int = 0


class MessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    role: str
    content: str
    model_id: str | None
    sender_user_id: str | None = None
    error: str | None
    created_at: datetime
    read: bool = False


class ChatIn(BaseModel):
    conversation_id: int
    content: str = Field(min_length=1, max_length=100_000)
    regenerate: bool = False

    @field_validator("content")
    @classmethod
    def strip_content(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("消息不能为空")
        return value
