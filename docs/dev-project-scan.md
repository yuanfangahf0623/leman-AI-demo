# 项目扫描记录

扫描日期：2026-05-07

扫描范围：`C:\Users\admin\Documents\New project`

## 扫描结论

当前目录不是 Git 仓库，且目录内仅发现 `AGENTS.md`。未发现后端源码、`pom.xml`、前端 `package.json`、数据库 SQL 脚本或已有业务模块代码。

因此，本文件只能记录当前可确认的仓库状态，以及基于 `AGENTS.md` 中项目约束推导出的后续模块放置建议。Spring Boot 版本、Maven 模块结构、前端启动/构建命令、数据库脚本目录和代码风格示例，当前无法从仓库源码中确认。

## 后端模块结构

当前扫描结果：

- 未发现后端父 `pom.xml`。
- 未发现 Maven 子模块。
- 未发现 `yudao-module-*`、`yudao-framework`、`yudao-server`、`yudao-dependencies` 等 yudao-cloud 常见目录。
- 未发现 Java 源码目录，例如 `src/main/java`。

基于 `AGENTS.md` 的项目约束：

- 项目目标风格：yudao-cloud / RuoYi 风格。
- 后端技术栈：Java、Spring Boot 3.x、MyBatis Plus、Spring AI。
- 新增 AI 知识库模块名固定为 `yudao-module-ai`。

待源码到位后需要确认：

- 后端父 POM 位置。
- `<modules>` 中的实际模块列表。
- `java.version`、`maven.compiler.release` 或 `maven.compiler.source/target`。
- Spring Boot 版本来源，例如父 POM、`dependencyManagement` 或 `spring-boot-dependencies`。

## 前端模块结构

当前扫描结果：

- 未发现前端项目目录。
- 未发现 `package.json`。
- 未发现 `pnpm-lock.yaml`、`yarn.lock`、`package-lock.json`。
- 未发现 `vite.config.*`、`vue.config.*`、`src` 等前端工程文件。

因此当前无法确认：

- 前端框架。
- 包管理器。
- 启动命令。
- 构建命令。

待源码到位后，应优先通过以下文件确认前端命令：

- `package.json` 的 `scripts`。
- 锁文件判断包管理器：`pnpm-lock.yaml`、`yarn.lock`、`package-lock.json`。

## Maven 编译命令

当前未发现 `pom.xml`，无法执行或确认 Maven 编译命令。

源码到位后，通常需要在后端父 POM 所在目录确认并执行：

```powershell
mvn clean compile
mvn clean package
```

如果项目存在 profile、跳过测试约定或模块化构建参数，应以父 POM 和项目 README 为准。

## 测试命令

当前未发现测试源码或 Maven POM，无法确认项目测试命令。

源码到位后，通常需要在后端父 POM 所在目录确认并执行：

```powershell
mvn test
```

如需按模块测试，应在确认模块名后使用：

```powershell
mvn -pl <module-name> test
```

## 前端启动命令

当前未发现 `package.json`，无法确认前端启动命令。

源码到位后，应从前端目录的 `package.json` 中确认，例如：

```powershell
pnpm dev
pnpm build
```

具体命令不得在未读取 `package.json` 前硬编码为项目事实。

## 数据库脚本位置

当前扫描结果：

- 未发现 `.sql` 文件。
- 未发现数据库初始化脚本目录。

待源码到位后，应重点查找以下常见位置：

- `sql/`
- `doc/sql/`
- `docs/sql/`
- `yudao-server/src/main/resources/sql/`
- 各模块 `src/main/resources/mapper/` 和初始化脚本目录

第一阶段 AI 知识库向量库按 `AGENTS.md` 约束使用 PostgreSQL + pgvector，相关初始化脚本后续应包含 pgvector 扩展、向量字段、索引和租户/知识库过滤字段。

## 已有模块代码风格

当前未发现任何 Java 模块或已有业务代码，因此无法从实际源码中归纳 DO、Mapper、Service、Controller、VO、Convert 的目录和命名方式。

待源码到位后，应按已有模块实际结构确认以下约定：

- DO：通常位于 `dal/dataobject` 或类似目录。
- Mapper：通常位于 `dal/mysql`、`dal/mapper` 或类似目录。
- Service：通常位于 `service`，接口与实现分层以项目现有风格为准。
- Controller：通常位于 `controller/admin`、`controller/app` 或类似目录。
- VO：通常位于 `controller/*/vo` 或类似目录。
- Convert：通常位于 `convert` 或类似目录。

在没有实际源码前，不应凭空固定包名或目录结构。

## 新增 yudao-module-ai 应该放在哪里

当前仓库未发现后端父 POM，无法给出实际落点。

根据 `AGENTS.md` 和 yudao-cloud 常见模块风格，源码到位后建议放置为后端父 POM 下的独立 Maven 模块：

```text
yudao-module-ai/
  yudao-module-ai-api/
  yudao-module-ai-biz/
```

并在后端父 `pom.xml` 的 `<modules>` 中注册对应模块。最终目录、包名和依赖关系必须以当前项目已有模块为准。

AI 知识库模块需要遵守以下设计约束：

- Controller 不写复杂业务逻辑。
- Service 负责业务编排。
- Mapper 只负责数据访问。
- 新增接口需要基础参数校验。
- 外部调用需要异常处理和日志。
- RAG 检索必须包含 `tenantId` 和 `knowledgeBaseId` 过滤。
- 第一阶段向量库使用 PostgreSQL + pgvector，并预留 Qdrant 适配。

## 本次扫描命令

```powershell
Get-ChildItem -Force
Get-Content -Raw -Encoding UTF8 -LiteralPath .\AGENTS.md
rg --files
git status --short
Get-ChildItem -Force -Recurse
Get-ChildItem -Force -Recurse -Include pom.xml,package.json,pnpm-lock.yaml,yarn.lock,package-lock.json,*.sql,*.java,*.vue,*.ts,*.js
Get-ChildItem -Force -Directory
```

备注：`rg --files` 在当前环境中执行失败，错误为 `Access is denied`；因此使用 PowerShell 原生命令完成扫描。
