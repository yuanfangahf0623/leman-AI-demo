# OCR 识别模块说明

## 目标

OCR 模块用于处理扫描件、图片型 PDF 和业务上传图片。普通 PDF 会优先使用 PDFBox 提取可复制文本；当提取文本为空或过短时，系统进入 OCR 流程，再进入切片和向量化。

当前策略：

1. 默认使用本地 Tesseract OCR。
2. 当 Tesseract 识别结果为空、过短或疑似乱码时，自动调用 OpenAI 兼容视觉模型兜底。
3. 视觉兜底默认模型为 `gpt-4o`，API Key 从 `AI_API_KEY` 读取，不允许写入代码或文档。

## 当前实现

- 图片识别统一抽象：`ImageRecognitionService`
- PDF OCR 抽象：`OcrService`
- 默认引擎：`TesseractCliOcrService`
- 混合调度：`HybridOcrService`
- 视觉兜底：`OpenAiCompatibleVisionOcrService`
- 接入位置：`PdfDocumentParser`、发票审核识别流程
- 默认状态：OCR 总开关默认关闭，开启后才处理扫描件或图片型 PDF

## 统一调用约定

- 所有图片内容识别统一依赖 `ImageRecognitionService`。
- PDF 文档解析仍通过 `OcrService` 处理文档级 OCR，内部复用同一套 Tesseract + 视觉兜底策略。
- 发票审核中的 PDF、JPG、PNG、TIFF 文件会先进入统一 OCR 能力，再按 `AI_INVOICE_RECOGNITION_PROVIDER` 决定是否调用模型抽取结构化发票字段；默认仍使用 mock，便于本地无模型环境联调。
- 后续如新增合同、证件、截图、二维码等图片识别场景，应接入 `ImageRecognitionService`，不要在业务 Service 中直接调用 Tesseract 或视觉模型。

## 环境变量

```powershell
$env:AI_DOCUMENT_OCR_ENABLED = "true"
$env:AI_DOCUMENT_OCR_PROVIDER = "tesseract-cli"
$env:AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE = "tesseract"
$env:AI_DOCUMENT_OCR_TESSDATA_DIRECTORY = ""
$env:AI_DOCUMENT_OCR_LANGUAGE = "chi_sim+eng"
$env:AI_DOCUMENT_OCR_DPI = "200"
$env:AI_DOCUMENT_OCR_MAX_PAGES = "20"
$env:AI_DOCUMENT_OCR_TIMEOUT_SECONDS = "60"
$env:AI_DOCUMENT_OCR_MIN_TEXT_LENGTH_TO_SKIP_OCR = "20"
```

视觉兜底配置：

```powershell
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_ENABLED = "true"
$env:AI_DOCUMENT_OCR_VISION_MODEL = "gpt-4o"
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_MAX_PAGES = "5"
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_MAX_IMAGE_BYTES = "6291456"
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_MIN_TEXT_LENGTH = "40"
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_GARBLED_RATIO_THRESHOLD = "0.25"
$env:AI_DOCUMENT_OCR_VISION_FALLBACK_TIMEOUT_SECONDS = "90"
```

视觉兜底复用以下模型配置：

```powershell
$env:AI_BASE_URL = "https://api.openai.com/v1"
$env:AI_API_KEY = "<your-api-key>"
```

发票字段结构化识别配置：

```powershell
# 默认 mock；设置为 model、llm 或 openai-compatible 时，才会调用 AI_CHAT_MODEL/AI_INVOICE_RECOGNITION_MODEL 抽取字段
$env:AI_INVOICE_RECOGNITION_PROVIDER = "model"
$env:AI_INVOICE_RECOGNITION_MODEL = ""
$env:AI_INVOICE_RECOGNITION_MAX_OCR_CHARS = "12000"
$env:AI_INVOICE_RECOGNITION_MAX_TOKENS = "1200"
$env:AI_INVOICE_RECOGNITION_FALLBACK_TO_MOCK = "true"
```

发票识别模型只接收 OCR 文本和文件类型，不会把 API Key、Token 或完整原始文件路径写入日志。模型调用失败时，如果 `AI_INVOICE_RECOGNITION_FALLBACK_TO_MOCK=true`，系统会回退到 mock 结果，避免阻塞流程联调。

如果 Tesseract 没有加入 `PATH`，请把 `AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE` 设置为本机完整可执行文件路径。
如果中文语言包不在 Tesseract 默认目录，请把 `AI_DOCUMENT_OCR_TESSDATA_DIRECTORY` 设置为包含 `chi_sim.traineddata`、`eng.traineddata` 的目录。

## 启动方式

修改环境变量后，使用项目脚本重启后端：

```powershell
.\scripts\start-yudao-server.ps1 -Build
```

## 验证步骤

1. 确认本机 OCR 引擎可用：

```powershell
tesseract --version
tesseract --list-langs
```

2. 确认语言包中包含 `chi_sim` 和 `eng`。
3. 上传扫描件 PDF。
4. 执行文档解析。
5. 检查文档切片是否生成。
6. 执行向量化。
7. 在问答页面提问，确认 citation 能引用该文档。

## 常见问题

- `OCR 引擎不可用`：Tesseract 未安装、未加入 `PATH`，或可执行文件路径配置错误。
- `OCR 识别失败`：语言包缺失、PDF 页面图片质量过低，或 Tesseract 处理失败。
- Tesseract 结果为空或乱码：如果视觉兜底已启用且模型配置完整，系统会自动调用 `AI_DOCUMENT_OCR_VISION_MODEL`。
- 视觉兜底未触发：检查 `AI_DOCUMENT_OCR_VISION_FALLBACK_ENABLED`、`AI_BASE_URL`、`AI_API_KEY` 和 `AI_DOCUMENT_OCR_VISION_MODEL`。
- 发票字段仍是 mock：检查 `AI_INVOICE_RECOGNITION_PROVIDER` 是否设置为 `model`、`llm` 或 `openai-compatible`，并确认 `AI_BASE_URL`、`AI_API_KEY`、`AI_CHAT_MODEL` 可用。
- 处理耗时较长：降低 `AI_DOCUMENT_OCR_MAX_PAGES`、`AI_DOCUMENT_OCR_VISION_FALLBACK_MAX_PAGES` 或 `AI_DOCUMENT_OCR_DPI`。

## 生产加固建议

- OCR 建议异步化，避免大文件阻塞 HTTP 请求。
- 对 OCR 页数、文件大小、并发数增加限流。
- OCR 临时文件目录建议独立挂载并定期清理。
- 视觉兜底会产生外部 API 调用成本，应保留页数、图片大小和超时限制。
- 所有 API Key 必须通过环境变量或配置中心注入，日志中不得打印完整密钥。
