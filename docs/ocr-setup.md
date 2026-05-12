# OCR 识别模块说明

## 目标

OCR 模块用于处理扫描件或图片型 PDF。普通 PDF 会优先使用 PDFBox 提取可复制文本；当提取文本为空或过短时，系统可回退到 OCR 识别，再进入切片和向量化流程。

## 当前实现

- OCR 抽象：`OcrService`
- 默认实现：`TesseractCliOcrService`
- 接入位置：`PdfDocumentParser`
- 默认状态：关闭
- 支持范围：第一阶段仅对 PDF 解析过程启用 OCR 回退

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
```

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
3. 上传扫描版 PDF。
4. 执行文档解析。
5. 解析成功后检查文档是否生成切片。
6. 执行向量化。
7. 在问答页面提问，确认 citation 能引用该文档。

## 常见问题

- `OCR 引擎不可用`：Tesseract 未安装、未加入 `PATH`，或可执行文件路径配置错误。
- `OCR 识别失败`：语言包缺失、PDF 页面图片质量过低，或 Tesseract 处理失败。
- 解析后仍无切片：OCR 未识别出有效文本，可提高 DPI 或检查扫描件清晰度。
- 处理耗时较长：降低 `AI_DOCUMENT_OCR_MAX_PAGES` 或 `AI_DOCUMENT_OCR_DPI`。

## 生产加固建议

- OCR 建议异步化，避免大文件阻塞 HTTP 请求。
- 对 OCR 页数、文件大小、并发数增加限流。
- OCR 临时文件目录建议独立挂载并定期清理。
- 后续可扩展云 OCR 或私有 OCR 服务，但 API Key 必须通过环境变量或配置中心注入。
