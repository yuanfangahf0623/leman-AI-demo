# FastGPT 本地自部署

本目录用于本机 Docker 部署 FastGPT，供理文科技 AI 知识库切换到 FastGPT RAG 引擎后联调使用。

## 端口

- FastGPT Web/API：`13000`
- FastGPT MCP：`13005`
- FastGPT MinIO API：`19000`
- FastGPT MinIO Console：`19001`

端口避开了本项目现有中间件的 `9000/9001`、`5432`、`3306`、`6379` 等端口。

## 启动

首次启动前，复制 `.env.example` 为 `.env`，并把所有 `<change-me>` 替换为本机私有值。不要提交 `.env`。

```powershell
cd F:\GitHub\leman-AI-demo
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml config
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml up -d
```

## 停止

```powershell
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml down
```

如需同时清空 FastGPT 数据卷，请确认数据不再需要后再执行：

```powershell
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml down -v
```

## 查看日志

```powershell
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml logs -f fastgpt-app
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml logs -f fastgpt-aiproxy
```

## 访问

- 本机访问：`http://localhost:13000`
- 局域网访问：`http://<Windows 虚拟机 IP>:13000`

默认管理员用户名为 `root`，密码来自 `.env` 中的 `FASTGPT_ROOT_PASSWORD`。

查看本机 root 密码：

```powershell
(Get-Content deploy/fastgpt/.env | Select-String '^FASTGPT_ROOT_PASSWORD=').Line -replace '^FASTGPT_ROOT_PASSWORD=', ''
```

登录后需要在 FastGPT / AI Proxy 中配置模型渠道，才能让 FastGPT 应用实际调用大模型。模型渠道可以继续使用公司现有的 OpenAI-compatible Base URL 和 API Key，但不要写入仓库文件。

## 使用群晖 MinIO

如果把群晖部署为 MinIO，FastGPT 可以直接使用群晖 MinIO 作为对象存储。建议在群晖 MinIO 中先创建两个 bucket：

- `fastgpt-public`
- `fastgpt-private`

然后修改 `deploy/fastgpt/.env`：

```powershell
FASTGPT_STORAGE_VENDOR=minio
FASTGPT_STORAGE_S3_ENDPOINT=http://<synology-ip>:9000
FASTGPT_STORAGE_EXTERNAL_ENDPOINT=http://<synology-ip>:9000
FASTGPT_MINIO_ROOT_USER=<synology-minio-access-key>
FASTGPT_MINIO_ROOT_PASSWORD=<synology-minio-secret-key>
```

修改后重启 FastGPT：

```powershell
docker compose --env-file deploy/fastgpt/.env -f deploy/fastgpt/docker-compose.yml up -d
```

如果已经在内置 MinIO 上传过文件，切换到群晖 MinIO 后旧文件不会自动迁移，需要先导出/迁移 bucket 数据，或者在测试阶段重新上传知识库文件。

## 接入本项目

在 FastGPT 页面创建应用并开启 API 访问，获取应用 Key 后，后端环境变量建议配置：

```powershell
$env:AI_RAG_ENGINE = "fastgpt"
$env:FASTGPT_BASE_URL = "http://127.0.0.1:13000/api"
$env:FASTGPT_API_KEY = "<FastGPT 应用 Key>"
$env:FASTGPT_MODEL = "fastgpt"
```

生产环境必须更换所有默认口令，并使用 HTTPS、固定域名、访问控制和日志审计。
