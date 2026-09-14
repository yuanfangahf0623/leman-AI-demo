# 192.168.19.239 知识库部署

部署日期：2026-09-08。当前阶段完成前后端上线和现有 Dify「知识库查询」接入；用户明确将本机历史文档、用户与会话迁移留到后续。

## 访问与运行

- 入口：<http://192.168.19.239:18081>，公司 `192.168.18.0/23` 网段及服务器本机可访问。
- 管理账号：`knowledge-admin`。独立密码保存在服务器 `/opt/data-center/secrets/knowledge-admin.json`（root、0600）和操作电脑 `C:\Users\admin\.codex\private\knowledge-192.168.19.239\admin-access.json`，不放入仓库。
- Dify 管理入口：<http://192.168.19.239:18080>，沿用原 Dify 账号。
- 后端：`leman-knowledge.service`，Java 17，仅监听 `127.0.0.1:48081`；开机启动、失败重启已启用。
- 内存 High 8GiB / Max 12GiB，Java 堆上限 6GiB，CPU 配额 4 核，swap 禁用；使用原先预留的 AI 前后端 12GiB。
- 当前发布：`/opt/data-center/apps/knowledge/releases/20260908_dify_v1`；`current` 软链接指向此目录。
- 前端由现有 Nginx 托管；日志 `/var/log/nginx/knowledge-*.log`、`/var/log/data-center/knowledge/backend.log`。
- MySQL 复用现有实例的 `yudao_ai` 库，应用账号仅持该库 CRUD 权限；发布时由 root 执行迁移，运行时关闭自动 SQL 初始化。
- 向量库复用 AI PostgreSQL 的 `yudao_ai` 库、`yudao_ai` 账号与 1536 维 `ai_vector_store`，不新增数据库实例。

## Dify 接入边界

系统配置 `ai.rag.engine=dify`。公司知识库（本地 ID 1、租户 1）明确绑定已有 Dify 应用与数据集；非绑定知识库不会被默默送入同一个 Dify 应用。
调用 Dify 前沿用本系统知识库可访问性和会话所有者检查。Dify 用户标识包含租户、知识库、用户、会话 ID；会话映射存入 `ai_dify_conversation`，外部 UUID 不冒充本地文档 ID。
回答、Token 用量和引用快照保存在本系统；返回引用的数据集必须匹配绑定值。

目前的两份资料仍在 **Dify 中维护**。本系统原有上传、解析和 pgvector 管线不会自动同步到 Dify；不要把本地文档列表为空理解为 Dify 没有资料。自动同步、本机历史数据迁移及多知识库应用映射另行实施。
OCR、Teams/邮箱同步、ChatGPT Actions 均未在此次启用；发票审批等非知识库模块未作为生产能力验收。

已发现现有项目 `AdminAuthServiceImpl` 将登录用户统一标为管理员并授予全部菜单权限。此次保留现有鉴权实现，当前用于管理员访问；**普通员工的分级角色权限尚未验收，不应直接按员工门户推广**。本次新增的 Dify 绑定校验不等于整个系统已具备完整 RBAC。

## 凭据与备份

`knowledge.env` 在服务器 root-only 配置目录，包含应用数据库及 Dify 应用凭据。OpenAI 密钥继续保存在原有加密恢复文件；启动前通过 Dify 私钥解密至 `/run/leman-knowledge/model.env`（tmpfs、root 0600），不写入发布包、仓库或磁盘明文配置。
专用服务 UID 的公网流量使用已有公司网关 `192.168.18.147`，内网访问按原路由处理，不依赖操作电脑在线。
初始化管理员密码只在首次启动使用；随后已关闭 bootstrap 并从环境文件移除，服务重启不会重置用户密码。

- 修改前备份：`/data/backup/knowledge-deploy/20260908_181744`（MySQL、Nginx）。
- 每日 04:00 的宿主机异机备份现覆盖知识库发布目录、文件存储 `/data/ai/knowledge`、配置和 MySQL 全库；新增服务纳入每分钟健康监控。
- AI PostgreSQL 仍由每日 03:30 Dify/AI 四库备份覆盖。
- 已完成 `host/20260908_182422` 的 NAS 上传与回读校验；最终前端发布后的备份 `host/20260908_183523` 同样成功，见 [部署证据](evidence/delivery-20260908.json)。
- 在线文件与数据库不是跨组件原子快照；恢复前核对文件、数据库和 Dify 的时点一致性。

## 实际构建与验收

本机原 JAR 被正在运行的进程占用，最终后端包在独立临时源码目录构建，未停止本机应用。

```powershell
$env:JAVA_HOME='C:\Users\admin\.codex\dev-env\tools\jdk-17'
mvn -B -ntp package
node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build --mode server
git diff --check
```

后端 149 项测试：144 通过、5 个需专用数据库的 pgvector 集成测试跳过；新增 Dify 测试覆盖成功响应、租户/知识库拒绝、未授权引用、上游失败与缺失回答。服务器另外实测了 pgvector 账号认证、向量运算及 OpenAI 官方接口访问。
前端生产构建成功；浏览器实际登录、打开 AI 问答和已有对话成功。真实问答返回来源引用，连续追问能记住文档，4 条消息与 Dify 会话映射经后端重启后仍保留。

服务器执行过的主要命令：

```bash
python3 deploy/production/prepare-host.py /opt/data-center/apps/knowledge/releases/20260908_dify_v1
systemd-analyze verify /etc/systemd/system/leman-knowledge.service
systemctl enable --now leman-knowledge.service
nginx -t
systemctl reload nginx
python3 /tmp/knowledge-verify.py
python3 /tmp/knowledge-finalize-host.py
python3 /tmp/knowledge-check-runtime.py
systemctl start data-center-foundation-health
systemctl start --no-block data-center-host-backup.service
```

`prepare-host.py` 是首次部署工具，会在修改前备份，已存在 `knowledge.env` 时拒绝再次初始化。后续升级不要直接重跑。
上述验证脚本保存受保护的完整问答；仅脱敏结果可复制到仓库。`check-runtime.py` 为只读验证，`verify-deployment.py` 会创建两轮真实问答。

## 回滚

首次部署需撤回时，先停止并禁用 `leman-knowledge.service`，取消 `/etc/nginx/sites-enabled/leman-knowledge` 软链接，执行 `nginx -t` 后 reload；不停止现有数据中台、Dify、Doris 或数据库。
保留数据库、文件、配置与备份，避免丢失上线后的新对话。后续升级优先保留旧发布目录，切回 `current` 并重启知识库服务。
数据库不能在有新业务数据时直接覆盖回部署前快照；需要恢复时先另做当前备份并核对影响范围。

## 本次修改文件

- 后端：`DifyProperties`、`DifyRagClient`、`AiDifyConversationMapper`、`RagServiceImpl`、`AiRagEngineConfigService`。
- 测试：`DifyRagClientTest`、`RagServiceImplTest`。
- 前端：`.env.server`、AI 问答页面的外部引擎名称、根 `.gitignore`。
- 部署：本目录中的 systemd、Nginx、SQL、初始化、凭据解密及验收脚本。
- 数据中台仓库：`ai-platform/foundation/collect-health.py`、`backup-host.sh`、资源台账及部署待办、证据。

原先已有的启动脚本与 `application.yml` 修改均保留；未提交 Git。
