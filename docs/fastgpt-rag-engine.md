# FastGPT RAG 引擎接入说明

## 目标

系统保留本地 RAG 链路，并新增 FastGPT 作为可切换 RAG 引擎。当前默认引擎为 `fastgpt`。

- `fastgpt`：权限、会话、消息、引用记录仍由本系统负责，知识检索和回答生成委托给 FastGPT 应用。
- `local`：使用本系统 PostgreSQL + pgvector、PromptBuilder、ChatModel 的本地 RAG 链路。

## 管理后台切换

运行时优先读取管理后台“系统管理 -> 参数配置”中的配置项：

- 参数名称：RAG 引擎
- 参数键名：`ai.rag.engine`
- 默认值：`fastgpt`
- 可选值：`fastgpt`、`local`

如果数据库里没有该配置项，系统才会回退读取环境变量 `AI_RAG_ENGINE` 或 `application.yml` 默认值。

## 环境变量

生产和测试环境不要把真实 Key 写入代码、配置样例或文档。启动后端前配置：

```powershell
$env:FASTGPT_BASE_URL = "https://your-fastgpt-domain"
$env:FASTGPT_API_KEY = "<your-fastgpt-app-api-key>"
$env:FASTGPT_APP_ID = "<optional-fastgpt-app-id>"
$env:FASTGPT_MODEL = "fastgpt"
$env:FASTGPT_CONNECT_TIMEOUT_SECONDS = "10"
$env:FASTGPT_READ_TIMEOUT_SECONDS = "120"
```

`FASTGPT_BASE_URL` 支持以下形式，系统会自动补齐到聊天接口：

- `https://your-fastgpt-domain`
- `https://your-fastgpt-domain/api`
- `https://your-fastgpt-domain/api/v1`
- `https://your-fastgpt-domain/api/v1/chat/completions`

## 调用方式

系统调用 FastGPT OpenAI 兼容聊天接口：

- Method：`POST`
- Path：`/api/v1/chat/completions`
- Header：`Authorization: Bearer <FASTGPT_API_KEY>`
- Body 关键字段：
  - `chatId`：由本系统租户和会话编号生成
  - `customUid`：当前登录用户编号
  - `stream=false`
  - `detail=true`
  - `messages`：当前问题和必要的历史上下文

## 本系统保留能力

- 登录态、租户、部门和知识库访问权限校验
- 会话创建、用户消息保存、助手消息保存
- FastGPT 返回的引用来源写入 `ai_chat_citation`
- 日志脱敏，不输出完整 API Key
- “我是谁”这类当前登录用户上下文问题仍在本系统内回答

## 注意事项

- FastGPT 应用自身需要完成知识库、数据集、检索参数和工作流配置。
- 本系统上传、解析、向量化到 pgvector 的文档不会自动同步到 FastGPT 数据集；如果要完全切换，需要单独设计同步流程。
- 选择“全部知识库”时，本系统会把请求交给同一个 FastGPT 应用，FastGPT 内部如何跨数据集检索由 FastGPT 应用配置决定。
- 如果 FastGPT 没有返回引用数组，系统仍会保存问答记录，但 citation 为空。
