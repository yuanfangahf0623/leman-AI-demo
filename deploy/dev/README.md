# 开发环境中间件

本目录提供本地开发使用的 Docker Compose 中间件编排，包含 MySQL、Redis、Nacos、PostgreSQL + pgvector、MinIO、Qdrant、RabbitMQ。

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
| MinIO API | `http://127.0.0.1:9000` | `minioadmin` | `minioadmin` |
| MinIO Console | `http://127.0.0.1:9001` | `minioadmin` | `minioadmin` |
| Qdrant HTTP | `http://127.0.0.1:6333` | 无 | 无 |
| Qdrant gRPC | `127.0.0.1:6334` | 无 | 无 |
| RabbitMQ | `127.0.0.1:5672` | `admin` | `rabbitmq123` |
| RabbitMQ Console | `http://127.0.0.1:15672` | `admin` | `rabbitmq123` |

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
```

## pgvector 验证

PostgreSQL 首次初始化时会执行 `postgres/init/01-create-pgvector.sql`，自动创建 `vector` 扩展。已有数据卷不会重复执行初始化脚本，如需重新初始化可执行：

```powershell
docker compose -f deploy/dev/docker-compose.middleware.yml down -v
docker compose -f deploy/dev/docker-compose.middleware.yml up -d postgres
```
