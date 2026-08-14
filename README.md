# ChatHub

微信式交互的 AI 模型聚合聊天**安卓客户端**：每个 AI 模型是一个"联系人"，豆包、DeepSeek、GPT 各自独立聊天，就像和好友发消息一样。

- 产品定义见 [pro.md](pro.md)，技术方案见 [tech.md](tech.md)，交互原型见 [prototype/index.html](prototype/index.html)
- 后端：FastAPI + SQLite(WAL) + SSE 流式
- 安卓端：Kotlin + Jetpack Compose（minSdk 26）

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 联系人 | 一个启用的模型即一个联系人，有头像、显示名、独立聊天窗口 |
| 聊天页 | 首页，最近对话列表（预览 / 时间 / 生成中状态） |
| 通讯录 | 全部模型联系人，按服务商分组 |
| Provider | 模型服务商（DeepSeek、豆包、OpenAI、Moonshot、智谱、通义、Ollama、自定义） |

## 快速开始

### 后端（二选一）

本地开发：

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Docker 一键启动：

```bash
docker compose up -d --build   # 数据落在 ./data/chat.db
```

### 安卓端

```bash
cd android
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

App 首次启动填写后端地址（开发机局域网 IP:8000 或线上域名）即可连接。

## 测试

```bash
cd backend && pytest           # 后端：适配器 / API 集成 / 模板 / regenerate
cd android && ./gradlew testDebugUnitTest   # 安卓：SseParser 单测
```

## 部署

- systemd：`deploy/chathub.service`（uvicorn 监听 127.0.0.1:8000，Caddy 反代 /api）
- 容器：`docker-compose.yml`（容器内访问宿主机 Ollama 时 baseUrl 用 `http://host.docker.internal:11434/v1`）

## 里程碑

- [x] M1 后端骨架：DB + CRUD + 模板 + 单测
- [x] M2 聊天链路：/api/chat SSE 透传 + 错误码 + mock 全链路测试
- [x] M3 安卓骨架：三 Tab 导航 + 聊天列表 + 聊天窗口流式渲染
- [x] M4 设置与联系人：服务商管理 + 连接测试 + 通讯录 + 联系人资料
- [x] M5 收尾：APK 打包、docker-compose、文档、SseParser 单测
- [ ] 验收：真实 Key 手工实测（见 pro.md §8）
