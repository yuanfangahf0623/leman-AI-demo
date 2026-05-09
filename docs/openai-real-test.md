# OpenAI 兼容接口真实联调指南

本文档用于 AI 知识库进入真实 OpenAI 兼容 API 联调和生产前加固阶段。文档只说明联调方式和排障要点，不包含任何真实 API Key、密码或 Token。

## 1. 环境变量配置

后端通过 `ai.model.*` 读取模型配置。生产、测试和本地联调都不要把真实密钥写入代码、配置样例、README 或测试文件。

PowerShell 示例：

```powershell
$env:AI_BASE_URL = "https://api.openai.com/v1"
$env:AI_API_KEY = "<your-api-key>"
$env:AI_CHAT_MODEL = "gpt-4o-mini"
$env:AI_EMBEDDING_MODEL = "text-embedding-3-small"
```

Linux / macOS 示例：

```bash
export AI_BASE_URL="https://api.openai.com/v1"
export AI_API_KEY="<your-api-key>"
export AI_CHAT_MODEL="gpt-4o-mini"
export AI_EMBEDDING_MODEL="text-embedding-3-small"
```

配置含义：

| 配置项 | 说明 |
| --- | --- |
| `AI_BASE_URL` | OpenAI 兼容服务地址，例如 `https://api.openai.com/v1` |
| `AI_API_KEY` | API Key，只能通过环境变量、配置中心或部署平台注入 |
| `AI_CHAT_MODEL` | Chat 模型名称 |
| `AI_EMBEDDING_MODEL` | Embedding 模型名称 |

后端配置需要确保：

```yaml
ai:
  model:
    provider: openai-compatible
    base-url: ${AI_BASE_URL}
    api-key: ${AI_API_KEY}
    chat-model: ${AI_CHAT_MODEL}
    embedding-model: ${AI_EMBEDDING_MODEL}
  vector-store:
    type: pgvector
    pgvector:
      jdbc-url: ${AI_PGVECTOR_JDBC_URL}
      username: ${AI_PGVECTOR_USERNAME}
      password: ${AI_PGVECTOR_PASSWORD}
      dimensions: 1536
```

注意：`ai.vector-store.pgvector.dimensions` 必须等于 Embedding 模型返回向量维度。`text-embedding-3-small` 默认维度通常为 1536。

本地 Docker Compose 默认 PostgreSQL 可配置为：

```powershell
$env:AI_PGVECTOR_JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/yudao_ai"
$env:AI_PGVECTOR_USERNAME = "postgres"
$env:AI_PGVECTOR_PASSWORD = "<your-postgres-password>"
```

不要在生产配置中复用开发密码。

## 2. 启动依赖服务

进入仓库根目录：

```powershell
cd F:\GitHub\leman-AI-demo
```

启动开发中间件：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml up -d
```

确认容器状态：

```powershell
docker ps
```

确认 PostgreSQL 已启用 pgvector：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d yudao_ai -c "SELECT extversion FROM pg_extension WHERE extname='vector';"
```

确认向量距离可用：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d yudao_ai -c "SELECT '[1,2,3]'::vector <-> '[1,2,4]'::vector AS distance;"
```

## 3. 启动后端

推荐先编译：

```powershell
mvn clean compile -DskipTests
```

启动后端：

```powershell
mvn -pl yudao-server spring-boot:run
```

也可以打包后启动：

```powershell
mvn clean package -DskipTests
java -jar yudao-server\target\yudao-server-1.0.0-SNAPSHOT.jar
```

后端默认端口：

```text
http://localhost:48080
```

## 4. 启动前端

进入前端目录：

```powershell
cd frontend\yudao-ui-admin-vue3
```

安装依赖：

```powershell
pnpm install
```

启动前端：

```powershell
pnpm dev
```

构建验证：

```powershell
pnpm build:prod
```

## 5. 测试 Embedding

Embedding 不建议直接通过前端单独调用，推荐通过“文档向量化”链路测试真实接口。

测试步骤：

1. 登录后台。
2. 进入“AI 知识库 / 知识库管理”。
3. 创建知识库，确认 `embeddingModel` 与 `AI_EMBEDDING_MODEL` 一致。
4. 进入“文档管理”，上传 TXT 文档。
5. 点击“解析”。
6. 点击“向量化”。
7. 查看后端日志，确认出现真实 Embedding 调用成功日志。

应重点确认：

- 调用地址为 `{AI_BASE_URL}/embeddings`
- HTTP Header 包含 `Authorization: Bearer <api-key>`
- 日志不打印完整 API Key
- 日志包含耗时 `elapsedMs`
- 日志包含返回向量维度 `dimensions`
- pgvector 表中存在对应向量记录

可用 SQL 检查向量记录：

```sql
SELECT vector_id, tenant_id, knowledge_base_id, document_id, chunk_id, length(content)
FROM ai_vector_store
ORDER BY id DESC
LIMIT 10;
```

## 6. 测试 Chat

Chat 推荐通过“问答测试”页面验证。

测试步骤：

1. 确认已有解析和向量化成功的文档。
2. 进入“AI 知识库 / 问答测试”。
3. 选择对应知识库。
4. 输入一个文档中确实存在答案的问题。
5. 点击发送。
6. 查看返回回答和引用来源。

应重点确认：

- 调用地址为 `{AI_BASE_URL}/chat/completions`
- HTTP Header 包含 `Authorization: Bearer <api-key>`
- 请求包含 system prompt 和 user prompt
- 日志不打印完整 API Key
- 日志包含耗时 `elapsedMs`
- 日志包含模型名 `model`
- 如果响应包含 `usage`，日志和问答消息记录中应保存 token 信息

可用 SQL 检查问答记录：

```sql
SELECT id, conversation_id, role, model, prompt_tokens, completion_tokens, total_tokens, latency_ms
FROM ai_chat_message
ORDER BY id DESC
LIMIT 10;
```

可用 SQL 检查引用记录：

```sql
SELECT id, message_id, document_id, chunk_id, document_title, score
FROM ai_chat_citation
ORDER BY id DESC
LIMIT 10;
```

## 7. 完整闭环测试

完整闭环为：

```text
上传 TXT -> 解析 -> 向量化 -> 问答 -> citation
```

建议测试文本：

```text
A 类设备点检周期为每天一次。
B 类设备点检周期为每周一次。
点检记录需要由设备管理员确认。
```

闭环步骤：

1. 创建知识库。
2. 上传上述 TXT 文件。
3. 在文档管理页面点击“解析”。
4. 确认 `parseStatus` 为成功。
5. 点击“向量化”。
6. 确认 `embeddingStatus` 为成功。
7. 在问答测试页面提问：`A 类设备点检周期是多久？`
8. 确认回答包含“每天一次”。
9. 确认页面展示 citation。
10. 确认 `ai_chat_message` 和 `ai_chat_citation` 已入库。

如果使用接口测试，顺序如下：

```text
POST /admin-api/ai/document/upload
POST /admin-api/ai/document/parse?id={documentId}
POST /admin-api/ai/document/embed?id={documentId}
POST /admin-api/ai/chat/completions
GET  /admin-api/ai/chat/citation/list?messageId={assistantMessageId}
```

## 8. 常见错误

### 8.1 401 Unauthorized

常见原因：

- `AI_API_KEY` 未配置
- API Key 错误、过期或被禁用
- 代理服务要求额外鉴权

处理建议：

- 重新确认环境变量是否在后端进程启动前设置
- 检查后端日志中的脱敏 API Key 前后缀是否符合预期
- 不要把完整 API Key 打印到日志或提交到仓库

### 8.2 404 Not Found

常见原因：

- `AI_BASE_URL` 配置错误
- base-url 多写或少写 `/v1`
- 兼容服务路径不是 OpenAI 标准路径
- 模型名称不存在

当前后端会自动拼接：

```text
{AI_BASE_URL}/embeddings
{AI_BASE_URL}/chat/completions
```

如果兼容服务要求完整路径，需要确认 `AI_BASE_URL` 是否应配置到 `/v1` 层级。

### 8.3 429 Too Many Requests

常见原因：

- API 配额不足
- 请求频率过高
- 批量向量化 chunk 过多

处理建议：

- 降低 `ai.document.embedding-batch-size`
- 降低并发任务数量
- 检查服务商配额
- 后续可增加重试、限流和队列削峰

### 8.4 pgvector 维度不匹配

典型现象：

- 向量写入失败
- 日志出现维度不一致
- PostgreSQL 报 `different vector dimensions`

常见原因：

- `ai.vector-store.pgvector.dimensions` 与 Embedding 模型实际输出维度不一致
- 已建表 `embedding vector(1536)`，但模型返回其他维度
- 切换 Embedding 模型后没有重建向量表或迁移数据

处理建议：

- 确认模型实际返回维度
- 调整 `ai.vector-store.pgvector.dimensions`
- 重建 `ai_vector_store` 表的 `embedding vector(n)` 字段
- 重新向量化历史文档

### 8.5 citation 为空

常见原因：

- 文档没有解析成功
- 文档没有向量化成功
- pgvector 中没有对应向量
- 问题和文档内容相似度低于 `scoreThreshold`
- `tenantId` 或 `knowledgeBaseId` 不匹配
- 知识库权限过滤不通过

处理建议：

- 检查 `ai_document.parse_status`
- 检查 `ai_document.embedding_status`
- 检查 `ai_document_chunk.status`
- 检查 `ai_vector_store` 是否有对应 `document_id`
- 临时降低知识库 `scoreThreshold`
- 确认问答时选择的知识库与文档所属知识库一致

## 9. 生产前加固清单

上线前至少确认：

- 真实 API Key 只来自环境变量、配置中心或部署平台
- 日志不打印完整 API Key
- RAG 检索强制包含 `tenantId`、`knowledgeBaseId`、`departmentId`
- 无命中时固定回答“根据当前知识库资料无法确认”
- 文档删除时同步逻辑删除 chunk，并删除对应向量
- pgvector 维度与 Embedding 模型维度一致
- 文档上传限制大小、后缀、文件名和基础内容格式
- 后续异步化前，同步解析和向量化接口需控制调用频率
