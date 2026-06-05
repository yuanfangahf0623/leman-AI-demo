# 开发环境中间件

本目录提供本地开发使用的 Docker Compose 中间件编排，包含 MySQL、Redis、Nacos、PostgreSQL + pgvector、MinIO、Qdrant、RabbitMQ。

OCR 使用本地 Tesseract CLI，由后端进程直接调用，不是独立网络服务。因此 Tesseract 需要安装在后端运行环境中；如果后端容器化部署，需要安装在后端镜像内，而不是单独放到本中间件 Compose 中。

## 安全说明

`docker-compose.middleware.yml` 中使用的是本地开发默认账号和密码，并通过环境变量提供覆盖能力。生产环境必须修改所有账号、密码、Token、端口暴露策略和网络访问控制，不要直接复用本文件。

如需覆盖默认值，可在启动命令前设置环境变量，或在 `deploy/dev/.env` 中配置。本仓库已忽略 `.env` 文件，避免误提交本地密钥。

## 启动

在仓库根目录执行：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml up -d
```

查看服务状态：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml ps
```

## 停止

停止并保留数据卷：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml down
```

停止并删除数据卷：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml down -v
```

## 查看日志

查看全部服务日志：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml logs -f
```

查看单个服务日志：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml logs -f postgres
docker compose -f deploy/dev/docker-compose.middleware.yml logs -f nacos
```

## 默认端口和账号

| 服务 | 地址 | 默认账号 | 默认密码 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306` | `root` / `yudao` | `root` / `yudao123` |
| Redis | `127.0.0.1:6379` | 无 | `redis123` |
| Nacos | `http://127.0.0.1:8848/nacos` | 本地默认未开启认证 | 本地默认未开启认证 |
| PostgreSQL + pgvector | `127.0.0.1:5432` | `postgres` | `postgres` |
| MinIO API | `http://192.168.19.246:9000` | configured on Synology | configured on Synology |
| MinIO Console | `http://192.168.19.246:9001` | configured on Synology | configured on Synology |
| Qdrant HTTP | `http://127.0.0.1:6333` | 无 | 无 |
| Qdrant gRPC | `127.0.0.1:6334` | 无 | 无 |
| RabbitMQ | `127.0.0.1:5672` | `admin` | `rabbitmq123` |
| RabbitMQ Console | `http://127.0.0.1:15672` | `admin` | `rabbitmq123` |

## AI document object storage

Backend document uploads use Synology MinIO by default:

```text
AI_DOCUMENT_STORAGE_TYPE=minio
AI_DOCUMENT_MINIO_ENDPOINT=http://192.168.19.246:9000
AI_DOCUMENT_MINIO_EXTERNAL_ENDPOINT=http://192.168.19.246:9000
AI_DOCUMENT_MINIO_BUCKET=yudao-ai-documents
AI_DOCUMENT_MINIO_REGION=us-east-1
AI_DOCUMENT_MINIO_ACCESS_KEY=<set in local/user env>
AI_DOCUMENT_MINIO_SECRET_KEY=<set in local/user env>
```

The local MinIO compose service is kept only as an explicit fallback profile:

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml --profile local-minio up -d minio
```

## OCR 运行依赖

扫描件或图片型 PDF 需要启用 OCR。当前实现依赖 Tesseract CLI，需要在后端运行环境安装：

Windows 本地开发：

```powershell
winget install --id tesseract-ocr.tesseract --exact --accept-package-agreements --accept-source-agreements
```

Linux 或后端容器镜像：

```bash
apt-get update
apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng
rm -rf /var/lib/apt/lists/*
```

后端容器化部署时，可参考 [Dockerfile.backend-runtime](./ocr/Dockerfile.backend-runtime) 把 OCR 依赖合并进实际后端镜像。

后端 OCR 常用环境变量：

```text
AI_DOCUMENT_OCR_ENABLED=true
AI_DOCUMENT_OCR_PROVIDER=tesseract-cli
AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE=tesseract
AI_DOCUMENT_OCR_TESSDATA_DIRECTORY=
AI_DOCUMENT_OCR_LANGUAGE=chi_sim+eng
AI_DOCUMENT_OCR_DPI=200
AI_DOCUMENT_OCR_MAX_PAGES=20
AI_DOCUMENT_OCR_TIMEOUT_SECONDS=60
```

如果 Windows 下 Tesseract 未加入 `PATH`，请把 `AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE` 设置为完整路径，例如 `C:\Program Files\Tesseract-OCR\tesseract.exe`。
如果中文语言包放在自定义目录，请设置 `AI_DOCUMENT_OCR_TESSDATA_DIRECTORY`。

## 常用环境变量

如果本机端口被占用，可覆盖端口，例如本机已有 PostgreSQL 使用 `5432`：

```powershell
$env:POSTGRES_PORT = "15432"
docker compose -f deploy/dev/docker-compose.middleware.yml up -d postgres
```

常用可覆盖变量：

```text
MYSQL_PORT, MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD
REDIS_PORT, REDIS_PASSWORD
NACOS_PORT, NACOS_GRPC_PORT, NACOS_RAFT_PORT, NACOS_AUTH_ENABLE
POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
MINIO_API_PORT, MINIO_CONSOLE_PORT, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD
QDRANT_HTTP_PORT, QDRANT_GRPC_PORT
RABBITMQ_PORT, RABBITMQ_MANAGEMENT_PORT, RABBITMQ_DEFAULT_USER, RABBITMQ_DEFAULT_PASS
AI_DOCUMENT_STORAGE_TYPE, AI_DOCUMENT_MINIO_ENDPOINT, AI_DOCUMENT_MINIO_EXTERNAL_ENDPOINT, AI_DOCUMENT_MINIO_BUCKET
AI_DOCUMENT_MINIO_ACCESS_KEY, AI_DOCUMENT_MINIO_SECRET_KEY, AI_DOCUMENT_MINIO_REGION
AI_DOCUMENT_OCR_ENABLED, AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE, AI_DOCUMENT_OCR_TESSDATA_DIRECTORY, AI_DOCUMENT_OCR_LANGUAGE
```

## pgvector 验证

PostgreSQL 首次初始化时会执行 `postgres/init/01-create-pgvector.sql`，自动创建 `vector` 扩展。已有数据卷不会重复执行初始化脚本，如需重新初始化可执行：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml down -v
docker compose -f deploy/dev/docker-compose.middleware.yml up -d postgres
```
