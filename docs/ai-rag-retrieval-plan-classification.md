# AI 知识库统一检索计划分类

## 目标

统一检索计划层用于在真正检索和构造 Prompt 之前，先判断：

- 用户问的是哪类业务问题。
- 该问题通常依赖哪些文件类型。
- 证据范围应该是局部片段、相邻片段、章节、全文，还是完整结构化数据集。
- 当前能力能否直接回答；如果不能，应该进入哪类执行器或返回无法确认。

核心原则：不要把所有问题都当成 `topK chunk` 问答处理，也不要为每一个具体业务问题新增一个临时直算分支。

## 当前已覆盖状态

| 能力 | 当前状态 | 说明 |
|---|---|---|
| 文件类型归类 | 已覆盖 | 已覆盖 TXT、Markdown、PDF、Word/WPS、Excel、PowerPoint |
| 问题类型归类 | 已覆盖 | 已覆盖事实问答、制度流程、故障排查、全文总结、翻译、审查、统计汇总、清单、多表关联等 |
| 检索计划生成 | 已接入 | `RetrievalPlanner` 会生成 `RetrievalPlan` |
| Prompt 调试输出 | 已接入 | `PromptBuilder` 会输出检索计划，方便排查为什么走某种策略 |
| 普通事实问答 | 已接入 | 仍走现有向量检索 + 词法补充 + Prompt |
| 统计/汇总/清单类结构化文档 | 部分接入 | 当前已对命中的结构化文档做完整 chunk 扩展，避免只看局部片段 |
| 表名清单统计 | 已有专项能力 | 当前 MES 数据表清单问题已有直接扫描 chunk 的能力，后续应收敛到结构化查询执行器 |
| 工装尺寸统计 | 已有专项能力 | 当前能完整合并文档 chunk 后统计，后续应收敛到结构化查询执行器 |
| 组织架构办公场景映射 | 已补充 | 已基于部门列表补充生产、品质、销售、财务、人资、设备、智造等部门场景覆盖情况 |
| 相邻 chunk 扩展 | 已分类，执行器待完善 | 当前只完成分类，后续需要统一 `AdjacentChunkRetriever` |
| 章节扩展 | 已分类，执行器待完善 | 后续按标题、目录、页码、Markdown heading 扩展 |
| 全文总结/翻译/审查 | 已分类，执行器待完善 | 后续需要 `FullDocumentRetriever`，长文档应分批处理 |
| 多表关联查询 | 已分类，架构待落地 | 后续需要结构化数据层和受控查询计划 |

## 当前支持文件类型

| 文件类型 | 扩展名 | 内容特点 | 默认推荐策略 | 典型升级场景 |
|---|---|---|---|---|
| TXT | `.txt` | 纯文本、日志、说明文、导出文本 | `LOCAL_CHUNK` / `ADJACENT_CHUNKS` | 全文总结、日志统计、全文翻译 |
| Markdown | `.md` | 有标题层级、技术文档、项目说明 | `LOCAL_CHUNK` / `SECTION_EXPANSION` | 章节问答、全文总结、清单统计 |
| PDF | `.pdf` | 制度、合同、扫描件、图文混排 | `LOCAL_CHUNK` / `ADJACENT_CHUNKS` | 合同翻译、合同审查、OCR 表格统计 |
| Word/WPS | `.doc` `.docx` `.wps` | 制度、合同、会议纪要、报告 | `LOCAL_CHUNK` / `SECTION_EXPANSION` | 全文总结、合同审查、表格抽取 |
| Excel | `.xls` `.xlsx` `.xlsb` | 台账、考勤、绩效、库存、人员清单 | `STRUCTURED_QUERY` | count、sum、group by、多表关联、权限过滤 |
| PowerPoint | `.ppt` `.pptx` `.pptm` | 汇报、方案、客户材料 | `LOCAL_CHUNK` / 页级扩展 | 全文总结、方案对比、客户信息提取 |

## 检索模式

| 模式 | 适用问题 | 证据范围 | 当前接入状态 |
|---|---|---|---|
| `LOCAL_CHUNK` | 普通事实问答 | 命中的 topK chunk | 已接入 |
| `ADJACENT_CHUNKS` | 流程、制度、排查 | 命中 chunk 前后文 | 已分类，执行器待完善 |
| `SECTION_EXPANSION` | 章节级问题 | 某个标题/章节下内容 | 已分类，执行器待完善 |
| `FULL_DOCUMENT` | 全文总结、全文翻译、全文审查 | 命中文档全文或分批全文 | 已分类，执行器待完善 |
| `STRUCTURED_QUERY` | 统计、汇总、清单、多表关联 | 完整数据集 | 结构化文档完整 chunk 扩展已接入，结构化查询引擎待落地 |

## 常见办公业务问题分类

| 问题类型 | 示例 | 推荐模式 | 推荐文件类型 | 关键要求 | 当前状态 |
|---|---|---|---|---|---|
| 事实问答 | “请假需要提前多久？” | `LOCAL_CHUNK` | Word、PDF、Markdown、TXT | 命中条款即可回答 | 已接入 |
| 制度流程 | “报销流程怎么走？” | `ADJACENT_CHUNKS` | Word、PDF、Markdown | 需要补齐前后步骤和例外条件 | 已分类 |
| IT/设备排查 | “二楼北电脑不能联网怎么排查？” | `ADJACENT_CHUNKS` | Word、PDF、TXT、PPT | 需要拓扑前后节点、处理步骤 | 已分类；多轮上下文已接入 |
| 全文总结 | “总结这份技术文档” | `FULL_DOCUMENT` | Word、PDF、TXT、Markdown、PPT | 长文档应分批总结再汇总 | 已分类 |
| 合同翻译 | “把德文合同翻译成中文” | `FULL_DOCUMENT` | PDF、Word、TXT | 必须覆盖全文，保留条款结构 | 已分类 |
| 合同审查 | “检查合同风险点” | `FULL_DOCUMENT` | PDF、Word | 需要全文审查和风险分类 | 已分类 |
| 表格统计 | “工装对应尺寸数量是多少？” | `STRUCTURED_QUERY` | Excel、Word 表格、PDF 表格 | 必须读完整数据集，不能只用 topK | 部分接入 |
| 数据表清单 | “MES 里有哪些数据表？” | `STRUCTURED_QUERY` | Word、PDF、Markdown、TXT | 需要全量扫描表名或结构化元数据 | 已有专项能力 |
| 多表关联 | “全勤且满绩效能拿多少钱？” | `STRUCTURED_QUERY` | Excel、结构化数据 | 需要考勤、绩效、人员、权限一起计算 | 已分类；查询引擎待落地 |
| 人员/工资/身份证 | “某人的工资是多少？” | `STRUCTURED_QUERY` | Excel、结构化数据 | 必须先做个人敏感数据权限判断 | 敏感权限策略已接入，结构化查询待落地 |
| 项目/RFQ 分析 | “这个客户是否值得跟进？” | `SECTION_EXPANSION` / `FULL_DOCUMENT` | Word、PDF、PPT、TXT | 取项目背景、客户、技术、商务片段 | 已分类 |
| 会议纪要追踪 | “上次会议有哪些待办？” | `STRUCTURED_QUERY` / `SECTION_EXPANSION` | Word、Markdown、TXT | 待办项适合抽取成结构化任务 | 已分类 |
| 日志/操作审计 | “今天失败了多少次登录？” | `STRUCTURED_QUERY` | TXT、Excel、数据库 | 需要完整日志或数据库查询 | 已分类 |
| 联网补充 | “知识库没有时查一下外网” | `LOCAL_CHUNK` + Web | Web | 外网结果只进聊天记录，不进标准知识库 | 已接入开关和记录 |

## 按当前组织架构映射的办公场景覆盖

本节基于《理文科技（山东）股份有限公司企业-部门列表导出.xlsx》中的部门层级梳理。该组织架构包含董事会办公室、生产制造部、供应链与物流部、品质保证部、项目管理部、工艺工程部、市场与销售部、设备动力部、智造工程部、财务与成本控制部、人力资源与行政部，以及生产、品质、财务、销售等下属班组或业务组。

| 部门/组织域 | 常见办公问题 | 推荐检索计划 | 当前覆盖判断 | 主要缺口 |
|---|---|---|---|---|
| 董事会办公室/公司管理层 | 会议纪要、决议、经营分析、跨部门事项追踪、制度查询 | `SECTION_EXPANSION` / `FULL_DOCUMENT` / `STRUCTURED_QUERY` | 部分覆盖 | 会议待办和经营指标需要结构化沉淀，不能只依赖文档片段 |
| 生产制造部 | 生产线 SOP、工艺参数、班组排班、异常处理、产量统计、工装/模具问题 | `ADJACENT_CHUNKS` / `SECTION_EXPANSION` / `STRUCTURED_QUERY` | 部分覆盖 | SOP 问答可覆盖，产量、排班、工装统计需要结构化查询或 MES 数据接入 |
| 汽车件车间及宝马/奥迪生产线 | 产品线资料、设备点检、工艺流程、质量异常、产线问题排查 | `ADJACENT_CHUNKS` / `SECTION_EXPANSION` | 部分覆盖 | 需要按产线、设备、工序建立元数据；排查类需要相邻 chunk 扩展执行器 |
| 模具工装车间/加工班/装配班 | 工装台账、尺寸统计、维修记录、加工/装配标准 | `STRUCTURED_QUERY` / `SECTION_EXPANSION` | 部分覆盖 | 工装尺寸已有专项统计，后续应转为通用结构化查询 |
| 供应链与物流部 | 供应商资料、采购交期、库存、物流异常、收发货记录 | `STRUCTURED_QUERY` / `FULL_DOCUMENT` | 已分类，未充分落地 | 需要供应商、采购、库存、物流台账结构化；多表关联能力是关键 |
| 品质保证部 | IQC/IPQC/OQC 标准、检验记录、客诉、8D、质量体系文件、测量报告 | `SECTION_EXPANSION` / `STRUCTURED_QUERY` | 部分覆盖 | 质量标准文档可问答，检验记录和客诉统计需要结构化抽取 |
| 来料检验/IQC/SQE | 来料检验标准、供应商质量问题、来料异常统计 | `SECTION_EXPANSION` / `STRUCTURED_QUERY` | 部分覆盖 | 供应商、物料、批次、异常原因需要结构化字段 |
| 过程质量/IPQC/OQC/终检包装 | 过程检验、终检标准、包装规范、不良统计 | `ADJACENT_CHUNKS` / `STRUCTURED_QUERY` | 部分覆盖 | 不良率、批次、工序维度统计需要完整数据集 |
| 实验测量室 | 测量报告、检具校准、实验记录、尺寸判定 | `SECTION_EXPANSION` / `STRUCTURED_QUERY` | 已分类，未充分落地 | 测量数据需要结构化字段、单位、上下限和判定规则 |
| 项目管理部 | RFQ、项目节点、APQP/PPAP、客户项目资料、风险跟踪 | `SECTION_EXPANSION` / `FULL_DOCUMENT` / `STRUCTURED_QUERY` | 部分覆盖 | 项目计划、节点、责任人、风险项建议抽取成结构化任务 |
| 工艺工程部/工艺工程组 | 工艺文件、工艺变更、作业指导书、技术说明、BOM/路线 | `SECTION_EXPANSION` / `STRUCTURED_QUERY` | 部分覆盖 | 工艺路线、BOM、参数表需要结构化抽取和版本管理 |
| 市场与销售部/外贸/内贸/电商 | 客户资料、RFQ 分析、合同翻译、合同审查、报价资料、商机跟进 | `FULL_DOCUMENT` / `SECTION_EXPANSION` / Web | 部分覆盖 | 合同全文翻译/审查已分类但执行器待完善，客户/RFQ 数据需要结构化 |
| 设备动力部 | 设备台账、点检保养、故障排查、备件清单、维修记录 | `ADJACENT_CHUNKS` / `STRUCTURED_QUERY` | 部分覆盖 | 点检周期可文档问答，维修历史和备件库存需要结构化 |
| 智造工程部 | MES 数据表、接口、自动化流程、报表口径、n8n/API 接入 | `STRUCTURED_QUERY` / `SECTION_EXPANSION` | 部分覆盖 | MES 表清单已有专项能力，后续要支持数据字典、表关系、接口元数据 |
| 财务与成本控制部/账务组/资金组 | 报销、付款、成本、预算、资金计划、应收应付统计 | `STRUCTURED_QUERY` / `SECTION_EXPANSION` | 已分类，需谨慎落地 | 涉及敏感财务数据，必须先做权限和字段级敏感策略 |
| 人力资源与行政部 | 考勤、绩效、薪资、请假、人员信息、行政制度、资产领用 | `STRUCTURED_QUERY` / `SECTION_EXPANSION` | 部分覆盖 | 个人敏感数据策略已接入，考勤/绩效/薪资联算还需要结构化执行器 |

### 组织架构视角的结论

1. 文档型办公问题已经基本覆盖分类：制度、流程、SOP、技术说明、合同、会议纪要、质量文件等，都能进入合适的检索计划。
2. 生产、品质、供应链、财务、人资这类高频业务，很多问题本质是“结构化数据查询”，不能只依赖 topK chunk。
3. 涉及统计、汇总、数量、金额、人员、工资、考勤、绩效、供应商、批次、库存、项目节点的问题，都应优先走 `STRUCTURED_QUERY`。
4. 涉及个人、工资、身份证、财务金额等内容时，必须先执行敏感数据策略和权限过滤，再决定是否检索或回答。
5. 当前方案已经覆盖“如何分类和选择检索计划”，但还没有完全覆盖“所有部门业务数据都能准确计算”。下一阶段重点应落在结构化抽取、结构化查询执行器、业务系统数据源接入和权限策略上。

## 文件类型与业务问题的推荐策略

| 文件类型 | 普通问答 | 流程/排查 | 章节问答 | 全文任务 | 统计/清单 | 多表关联 |
|---|---|---|---|---|---|---|
| TXT | `LOCAL_CHUNK` | `ADJACENT_CHUNKS` | `SECTION_EXPANSION`（弱） | `FULL_DOCUMENT` | `STRUCTURED_QUERY`（日志/清单） | 不推荐，需先结构化 |
| Markdown | `LOCAL_CHUNK` | `ADJACENT_CHUNKS` | `SECTION_EXPANSION` | `FULL_DOCUMENT` | `STRUCTURED_QUERY`（清单/表名） | 不推荐，需先结构化 |
| PDF | `LOCAL_CHUNK` | `ADJACENT_CHUNKS` | `SECTION_EXPANSION`（依赖目录/OCR） | `FULL_DOCUMENT` | `STRUCTURED_QUERY`（表格/OCR 后） | 需先表格抽取 |
| Word/WPS | `LOCAL_CHUNK` | `ADJACENT_CHUNKS` | `SECTION_EXPANSION` | `FULL_DOCUMENT` | `STRUCTURED_QUERY`（表格抽取后） | 需先表格抽取 |
| Excel | `LOCAL_CHUNK`（少用） | 不推荐 | Sheet/区域扩展 | Sheet 分批 | `STRUCTURED_QUERY` | `STRUCTURED_QUERY` |
| PowerPoint | `LOCAL_CHUNK` | 页级上下文 | 页/章节扩展 | `FULL_DOCUMENT` | 不推荐，需抽取表格 | 不推荐 |

## 当前代码落点

- `RetrievalFileTypeEnum`：文件类型分组。
- `RetrievalQuestionTypeEnum`：业务问题类型。
- `RetrievalModeEnum`：检索模式。
- `RetrievalPlan`：检索计划对象，记录模式、问题类型、偏好文件类型、是否要求完整证据。
- `RetrievalPlanner`：根据用户问题生成检索计划。
- `PromptBuilder`：把检索计划写入 debugInfo。
- `RagServiceImpl`：当前先把 `STRUCTURED_QUERY` 接入结构化文档完整 chunk 扩展逻辑。

## 代码当前能直接生效的能力

1. 普通知识库问答仍走原 RAG 链路。
2. 问题调试信息会显示检索计划，便于判断分类是否正确。
3. 统计、汇总、清单类问题会被归到 `STRUCTURED_QUERY`。
4. 命中结构化文档时，会读取同一 `documentId` 下的完整有效 chunk，避免只看局部片段。
5. 现有工装尺寸统计和 MES 表清单能力仍可用，但后续应收敛到通用结构化查询执行器。

## 后续执行器拆分

建议下一步拆分以下执行器：

- `LocalChunkRetriever`：普通 topK chunk 检索。
- `AdjacentChunkRetriever`：命中 chunk 前后文扩展。
- `SectionRetriever`：按标题、页码、Markdown heading、Word 段落结构扩展。
- `FullDocumentRetriever`：全文总结、全文翻译、全文审查，支持长文档分批。
- `StructuredQueryRetriever`：Excel、Word 表格、PDF 表格抽取后的结构化查询。
- `WebSupplementRetriever`：联网补充，只写聊天记录和问答记录。

其中 `StructuredQueryRetriever` 是支撑大规模使用的核心，不能长期依赖模型阅读 chunk 后自行计数。

## 后续结构化查询方向

结构化查询执行器需要逐步支持：

- 表格抽取：Excel Sheet、Word 表格、PDF/OCR 表格。
- 字段识别：字段名、别名、类型、单位。
- 行级数据：每一行用 JSON 或结构化表保存。
- 关系维护：人员表、考勤表、绩效表、工资表之间的关联。
- 权限控制：tenantId、knowledgeBaseId、departmentId、userId、个人敏感数据策略。
- 受控查询计划：由系统生成 DSL/SQL，不允许模型直接自由拼 SQL。
- 聚合能力：count、sum、avg、min、max、distinct、group by、filter。
