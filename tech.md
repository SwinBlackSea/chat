# ChatHub 技术方案（tech.md）

与 pro.md 对应：本文档定义 MVP（P0）的技术实现，P1/P2 只预留扩展位。

## 1. 总体架构

```
安卓客户端 (Kotlin + Jetpack Compose)
   │  REST(JSON) + 聊天流(POST → SSE) + 实时通道(WebSocket /api/ws)，OkHttp
后端 (FastAPI, async)
   ├── REST API：providers / models / conversations / messages
   ├── Chat 服务：组装上下文 → 调适配器 → 流式转发 → 落库（SSE，AI 链路）
   ├── WS 实时通道：连接管理 + 消息转发 / 心跳（人人通信，独立于 SSE）
   └── Provider 适配层（统一接口）
        ├── OpenAI 兼容客户端（主干，覆盖所有 OpenAI 兼容服务商，含 Hermes）
        └── dshweb RPC 适配器（session.prompt + 轮询，非 OpenAI 兼容）
上游：DeepSeek / 火山方舟(豆包) / OpenAI / Moonshot / 智谱 / 通义 / Ollama /
     Hermes（agent） / dshweb（agent）...
存储：SQLite（WAL 模式），文件位于 data/chat.db
```

关键决策：

- DeepSeek、火山方舟、Moonshot、智谱、通义、Ollama 均提供 OpenAI 兼容的
  `/chat/completions` 接口，因此适配层只实现**一个** OpenAI 兼容客户端作为主干，
  服务商差异用"模板"（预填 baseUrl + 默认模型清单）表达。
- agent 类服务商（Hermes / dshweb）模板标记 `agent=True`：聊天链路放宽超时；
  Hermes 复用 OpenAI 兼容客户端，dshweb 走独立 RPC 适配器（每次请求新建会话，
  prompt 完整上下文 → 轮询 running=false → 取最后一条 assistant 回复）。
- 聊天链路为 SSE 透传：安卓端 POST → 后端用 httpx 流式请求上游 → 逐 chunk
  解析并 yield → 安卓端逐行解析 SSE。后端不缓冲全文（dshweb 无流式，
  最终回复按小块 yield 模拟打字机）。
- **两条独立通道**：AI 流式固定走 SSE（POST /api/chat，请求-响应式、单向）；
  人人通信的实时推送走 WebSocket（/api/ws，常驻双向）。互不混用，
  AI 链路不受实时通道影响（见 §5.1 协议）。
- 模型即联系人：一个模型（联系人）在 MVP 中对应唯一一条会话线程
  （conversations.model_id UNIQUE），上下文互不串扰。

## 2. 技术栈

- 后端：Python 3.11+，FastAPI，Uvicorn，SQLAlchemy 2.x + SQLite(WAL)，
  httpx（异步流式），pydantic v2，aiosqlite（SQLAlchemy 异步驱动）
- 安卓端：Kotlin 2.x，minSdk 26，Jetpack Compose + Material 3，
  Navigation Compose（导航），OkHttp（REST + SSE 流读取），
  DataStore（保存后端地址等轻量配置），ViewModel + StateFlow + Coroutines/Flow
- 质量工具：ruff（后端 lint/format），pytest + pytest-asyncio + respx（后端测试，
  respx 用于 mock 上游 HTTP）；安卓端 Gradle（Kotlin DSL）+ JUnit（SseParser 单测）
- 部署：docker-compose（backend 单服务）；安卓 APK 由 Gradle 打包安装

## 3. 目录结构

```
chat/
├── backend/
│   ├── app/
│   │   ├── main.py              # FastAPI 入口、路由挂载、CORS
│   │   ├── config.py            # 配置（环境变量 + 默认值）
│   │   ├── db.py                # engine / session / 建表
│   │   ├── models.py            # ORM 表定义
│   │   ├── schemas.py           # pydantic DTO
│   │   ├── api/
│   │   │   ├── providers.py     # 服务商 + 模型管理 + 连接测试
│   │   │   ├── conversations.py # 聊天列表 / 会话 / 消息 / 清空记录
│   │   │   ├── chat.py          # POST /api/chat（SSE）
│   │   │   └── ws.py            # /api/ws WebSocket 实时通道（独立于 SSE）
│   │   ├── services/
│   │   │   ├── chat.py          # 上下文组装、消息落库
│   │   │   └── ws.py            # 连接管理器（注册/推送/广播/清理）
│   │   └── providers/
│   │       ├── base.py          # 适配器接口 + 事件类型 + 错误码
│   │       ├── openai_compat.py # OpenAI 兼容客户端（主干实现，含 Hermes）
│   │       ├── dshweb_adapter.py # dshweb RPC 适配器（session.prompt + 轮询）
│   │       └── templates.py     # 内置服务商模板（含 agent 标记）
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
├── android/
│   ├── app/src/main/java/online/xiaoxi/chathub/
│   │   ├── MainActivity.kt
│   │   ├── ui/
│   │   │   ├── setup/           # 首启：填后端地址 + 校验
│   │   │   ├── chatlist/        # 聊天页（首页）
│   │   │   ├── chat/            # 聊天窗口（流式渲染）
│   │   │   ├── contacts/        # 通讯录（按服务商分组）
│   │   │   ├── contactinfo/     # 联系人资料 / 人设 / 清空记录
│   │   │   └── settings/        # 服务商管理 + 添加服务商
│   │   ├── data/
│   │   │   ├── ApiClient.kt     # 全部后端 REST 调用收敛于此
│   │   │   ├── SseParser.kt     # SSE 逐行解析（唯一解析点）
│   │   │   └── Dto.kt           # 与后端 API 对应的数据结构
│   │   └── theme/
│   └── build.gradle.kts / settings.gradle.kts / gradle wrapper
├── data/                        # SQLite 数据卷（gitignore）
├── docker-compose.yml
├── prototype/index.html         # 微信式交互原型（静态演示）
├── pro.md / tech.md / AGENTS.md
```

## 4. 数据模型（SQLite）

- providers: `id`, `name`, `kind`(模板标识或 custom), `base_url`, `api_key`,
  `is_enabled`, `created_at`, `updated_at`
- models: `id`, `provider_id`(FK→providers, CASCADE), `model_id`(上游模型名),
  `display_name`(联系人显示名), `avatar_color`(头像色，可空), `context_length`(可空),
  `is_enabled`(停用即从通讯录隐藏), `sort`
- conversations: `id`, `model_id`(FK→models, CASCADE, **UNIQUE**),
  `system_prompt`(联系人人设), `created_at`, `updated_at`
- messages: `id`, `conversation_id`(FK→conversations, CASCADE),
  `role`(user/assistant/system), `content`, `model_id`(可空),
  `prompt_tokens`, `completion_tokens`, `duration_ms`, `error`(可空), `created_at`

要点：

- 模型即联系人：MVP 每个模型唯一一条会话线程（UNIQUE 约束）；
  "清空聊天记录" = 删除该会话下 messages（会话保留），上下文随之重置。
- 联系人被删除：会话与消息级联删除；联系人停用：仅隐藏入口，数据保留。
- P1 若要支持同一联系人多会话，解除 UNIQUE 约束即可，表结构不变。
- 聊天不需要"选模型"：发消息的目标模型由联系人（会话）决定。

## 5. API 设计

REST（JSON，前缀 `/api`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/health | 健康检查（安卓端连通性校验） |
| GET / POST | /api/providers | 列表（Key 脱敏）/ 创建 |
| PUT / DELETE | /api/providers/{id} | 更新 / 删除（级联删模型与联系人） |
| POST | /api/providers/{id}/test | 连接测试 |
| GET / POST | /api/providers/{id}/models | 模型列表 / 添加联系人 |
| PATCH / DELETE | /api/models/{id} | 启停 / 删除 |
| GET | /api/models | 全部启用模型（通讯录用） |
| GET | /api/conversations | 聊天列表：联系人信息 + 最后消息预览 + 时间，按最后消息倒序 |
| POST | /api/conversations | 按 model_id 幂等创建（已存在则返回现有） |
| GET | /api/conversations/{id}/messages | 历史消息 |
| PUT | /api/conversations/{id} | 更新人设（system_prompt） |
| DELETE | /api/conversations/{id}/messages | 清空聊天记录 |

聊天流：

```
POST /api/chat
body: { "conversation_id": 1, "content": "你好" }   # 模型由会话对应联系人决定
resp: text/event-stream

event: token   data: {"delta": "你"}          # 逐 token
event: token   data: {"delta": "好"}
event: done    data: {"message_id": 42, "usage": {"prompt_tokens": 10, "completion_tokens": 8}}
event: error   data: {"code": "bad_key", "message": "API Key 无效"}
```

- 语义：收到请求即把 user 消息落库 → 流式调上游 → assistant 消息边收边拼，
  done 时整体落库（含 usage/耗时）；error 时也落一条带 error 字段的 assistant 消息。
- 重新生成 = 安卓端重发最后一条 user 内容，替换上一条 assistant 消息，无额外接口。
- 客户端中断：安卓端 cancel OkHttp call → FastAPI 检测断连 → 取消上游请求。

### 5.1 WebSocket 实时通道（人人通信，独立于 AI SSE 链路）

连接：`ws://<host>/api/ws?client_id=<用户标识>`（MVP 无用户体系，握手自报身份；
后续接入 users 表后改为鉴权后从会话推导）。消息为 JSON 文本帧：

| 方向 | 类型 | payload | 说明 |
| --- | --- | --- | --- |
| 上行 | ping | `{}` | 心跳，服务端回 `{"type":"pong"}` |
| 上行 | message | `{"to":"<client_id>","content":"..."}` | 发给指定用户 |
| 下行 | message | `{"from":"<client_id>","content":"..."}` | 服务端转发给接收方全部在线连接 |
| 下行 | error | `{"code":"offline"\|"bad_request","message":"..."}` | 对方不在线 / 请求非法 |

- 连接管理（services/ws.py）：内存注册表 `client_id → 连接集合`（多端在线全送达），
  断线自动清理；发送失败即剔除该连接。
- 通道独立：不碰 /api/chat、不碰 providers 适配层；AI 流式仍走 SSE。
- 后续演进（不在本轮）：users/会话成员表、消息落库、离线补推、在线状态事件、已读回执。

## 6. Provider 适配层

统一接口（providers/base.py）：

```python
class ChatAdapter:
    async def chat_stream(self, req: ChatRequest) -> AsyncIterator[StreamEvent]: ...

# StreamEvent = TokenDelta(delta) | Done(usage) | ProviderError(code, message)
```

openai_compat.py：httpx AsyncClient 以流式 POST `{base_url}/chat/completions`
（stream=True），逐行解析 SSE `data:` 行，yield token 增量，识别 `[DONE]`。
连接测试：非流式最小请求（messages 一条、max_tokens=1），超时 10s。

内置模板（templates.py，均可在 UI 中修改）：

| kind | 名称 | baseUrl | 备注 |
| --- | --- | --- | --- |
| deepseek | DeepSeek | https://api.deepseek.com | 默认模型 deepseek-chat、deepseek-reasoner |
| doubao | 豆包(火山方舟) | https://ark.cn-beijing.volces.com/api/v3 | 模型名支持接入点 ID（ep-xxx）或模型名 |
| openai | OpenAI | https://api.openai.com/v1 | gpt-4o-mini 等 |
| moonshot | Moonshot | https://api.moonshot.cn/v1 | kimi 系列 |
| zhipu | 智谱 GLM | https://open.bigmodel.cn/api/paas/v4 | glm 系列 |
| qwen | 通义千问 | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen 系列 |
| ollama | Ollama(本地) | http://localhost:11434/v1 | 模型清单留空由用户添加 |
| hermes | Hermes | 用户填写 | agent 类：自托管 OpenAI 兼容 gateway（Bearer + SSE），走 openai_compat 适配器 |
| dshweb | dshweb（DeepSeek Harness） | 用户填写 | agent 类：自托管 RPC（session.prompt + 轮询），走 dshweb_adapter |
| custom | 自定义 | 用户填写 | 任意 OpenAI 兼容端点 |

模板只是快捷方式：默认模型清单用户可自由增删改，不是硬约束。
上游模型迭代快，清单过时不影响可用性（手动加联系人即可）。

agent 类模板（hermes / dshweb，`agent=True`）：模型清单留空由用户添加；
聊天链路按 agent 超时策略执行（见 §7），连接测试走对应适配器的最小请求。

## 7. 流式与错误处理（对齐 AGENTS.md 三原则）

- 正常一溜到底：chunk 到达即转发，后端不缓冲全文；落库失败不阻断聊天流。
- 异常快速抛错：
  - 超时：连接测试 10s；普通聊天首 token 60s、整体 300s；agent 类服务商
    （hermes / dshweb，多步工具调用耗时长）首 token 300s、整体 3600s，均可用环境变量覆盖。
    安卓端 OkHttp 读超时与后端对齐（agent 类放宽至 30 分钟级）。
  - 上游 4xx/5xx：解析响应体提取可读信息 → yield error 事件 → 客户端明确提示。
  - 统一错误码：`bad_key`(401) / `insufficient_balance` / `rate_limit` /
    `model_not_found` / `upstream_error` / `network_error` / `timeout`。
- 失控交还用户：生成中"停止" = 客户端 cancel + 后端取消上游；
  任何错误都保留已生成部分与错误提示，不清空现场。

上下文策略：MVP 全量携带该联系人历史；超长由上游报错并按错误码提示，
自动截断放 P1。

## 8. 安全

- API Key 存服务端 SQLite；REST 响应一律脱敏（前 4 后 4，中间 `****`）。
- Key 不写日志、不进错误消息；安卓端不缓存 Key，只持有后端地址（DataStore）。
- MVP 后端无用户鉴权（前提：自有服务器、仅自己访问）；CORS 按需收紧。
- 后端地址使用 HTTPS 域名（现有 Caddy 证书）；P2 多用户时再加鉴权。

## 9. 测试策略

- 适配器层单测（核心）：respx mock 上游，覆盖正常流、分片 chunk、
  上游错误、超时、[DONE] 边界。
- API 集成测试：providers/models/conversations CRUD、连接测试接口、
  聊天列表与清空记录、/api/chat（mock 上游走通 SSE 全链路）。
- 模板校验测试：所有模板字段齐全、kind 唯一。
- 安卓端：SseParser JVM 单测（分片、乱序行、error 事件、[DONE]）；
  UI 自动化放 P1。
- 手工验收清单：按 pro.md §7，用真实 Key 实测 DeepSeek 与豆包。

## 10. 运行与部署

后端开发模式：

```bash
cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8000
```

安卓端开发模式：Android Studio 打开 `android/`，真机/模拟器运行；
App 内填写后端地址（开发机局域网 IP:8000 或线上域名）。

生产模式（当前服务器已部署）：
- systemd 单元 `chathub.service`（仓库内 deploy/chathub.service）运行 uvicorn，
  监听 127.0.0.1:8000；`sudo systemctl enable --now chathub` 启动
- Caddy 将 `https://chat150.xiaoxi.online/api/*` 反代到后端（保留 /api 前缀）
- 安卓端"后端地址"填 `https://chat150.xiaoxi.online`
- 数据文件：`data/chat.db`
- 容器化部署（仓库根 docker-compose.yml，已提供）：`docker compose up -d --build`，
  数据落在 ./data/chat.db；容器内访问宿主机 Ollama 时 baseUrl 需改为
  `http://host.docker.internal:11434/v1`

安卓打包：`cd android && ./gradlew assembleDebug`，
产物 `app/build/outputs/apk/debug/app-debug.apk`；release 签名与分发放 P1。

配置项（环境变量）：`CHAT_DB_PATH`（默认 data/chat.db）、`CHAT_TIMEOUT_FIRST_TOKEN`、
`CHAT_TIMEOUT_TOTAL`、`CHAT_TIMEOUT_AGENT_FIRST_TOKEN`（默认 300）、
`CHAT_TIMEOUT_AGENT_TOTAL`（默认 3600）。不使用 .env 入库。

## 11. 里程碑

- M1 后端骨架：DB + providers/models/conversations CRUD + 模板 + 单测
- M2 聊天链路：/api/chat SSE 透传 + 错误码 + mock 全链路测试
- M3 安卓骨架：底部三 Tab 导航 + 聊天列表 + 聊天窗口流式渲染（markdown/停止/重生成）
- M4 设置与联系人：服务商管理 + 连接测试 + 通讯录 + 联系人资料（人设/清空记录）
- M5 收尾：APK 打包、docker-compose、文档校对、真实 Key 验收
