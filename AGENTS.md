# AGENTS.md

## 核心准则

正常时一溜到底，异常时不拉稀摆带（快速抛错止损），失控时把主动权交还给用户。

## 项目概述

ChatHub：微信式交互的 AI 模型聚合聊天安卓客户端——每个模型是一个联系人，
各自独立聊天窗口。后端 FastAPI + SQLite + SSE，安卓端 Kotlin + Jetpack Compose。
产品定义见 pro.md，技术方案见 tech.md，交互原型见 prototype/index.html。
**修改功能时必须同步更新对应文档**；文档与代码冲突时，先确认再动手。

## 常用命令

- 后端开发：`cd backend && uvicorn app.main:app --reload --port 8000`
- 后端测试：`cd backend && pytest`
- 安卓构建：`cd android && ./gradlew assembleDebug`
- 安卓单测：`cd android && ./gradlew test`
- 后端格式化/lint：`ruff format . && ruff check .`

## 架构红线

- 所有上游模型调用必须经过 `backend/app/providers/` 适配层，
  禁止在 API 层/客户端硬编码任何服务商 URL 或协议细节。
- 新服务商优先走 OpenAI 兼容模板（templates.py）；确不兼容才新增独立适配器。
- 流式输出统一 SSE（POST /api/chat）；不引入 WebSocket。
- 聊天链路（上下文组装、消息落库、调上游）只走 services 层；
  服务商/会话的简单 CRUD 可在 API 层直接完成。
- 安卓端所有后端调用收敛在 `data/ApiClient.kt`，SSE 解析只在 `data/SseParser.kt`；
  UI 层不直接发网络请求。
- 模型即联系人：会话与模型一一对应（MVP），不做"聊天中切模型"。

## 编码规范

- Python：类型注解 + async IO；不用 print，用 logging；异常不吞，
  按"快速抛错"原则尽早抛出并携带可读信息。
- Kotlin：遵循官方代码风格；UI 用 Compose（不在 MVP 引入 XML 布局）；
  状态用 StateFlow，异步用 Coroutines/Flow。
- 命名：后端 snake_case，安卓端 Kotlin 官方规范（camelCase/PascalCase）。
- 不加多余抽象：MVP 阶段以 tech.md 的结构为准，不提前实现 P1/P2。

## 安全红线

- API Key 不写日志、不进错误消息、不写死在代码里；对外展示一律脱敏。
- 安卓端不缓存 Key，只存后端地址。
- data/、*.db、.env 不入库（gitignore）。

## Git 约定

- 提交信息用祈使句，说清动机；按里程碑粒度提交（见 tech.md §11）。
