package cn.iocoder.yudao.module.ai.framework.parser;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Excel 文档解析器，支持 xls、xlsx 和 xlsb。
 */
@Component
public class ExcelDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("xls", "xlsx", "xlsb");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "ms-excel")
                || DocumentParseUtils.contentTypeContains(contentType, "spreadsheetml")
                || DocumentParseUtils.contentTypeContains(contentType, "sheet.binary");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return parseWorkbook(bytes, context);
        } catch (Exception ex) {
            throw new DocumentParseException("Excel 文档解析失败", ex);
        }
    }

    private ParsedDocument parseWorkbook(byte[] bytes, DocumentParseContext context) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            StringBuilder builder = new StringBuilder();
            int rowCount = 0;
            int cellCount = 0;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                builder.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    rowCount++;
                    for (Cell cell : row) {
                        cellCount++;
                        builder.append(formatter.formatCellValue(cell)).append('\t');
                    }
                    builder.append('\n');
                }
            }
            String content = builder.toString();
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(),
                    DocumentParseUtils.extension(context.getFilename()));
            metadata.put("sheetCount", workbook.getNumberOfSheets());
            metadata.put("rowCount", rowCount);
            metadata.put("cellCount", cellCount);
            metadata.put("charCount", content.length());
            return new ParsedDocument(DocumentParseUtils.titleFromFilename(context.getFilename(), "Excel Document"),
                    content, metadata);
        } catch (Exception ex) {
            if ("xlsb".equals(DocumentParseUtils.extension(context.getFilename()))) {
                return parseXlsbWithExtractor(bytes, context);
            }
            throw ex;
        }
    }

    private ParsedDocument parseXlsbWithExtractor(byte[] bytes, DocumentParseContext context) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(new ByteArrayInputStream(bytes))) {
            String content = extractor.getText();
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(), "xlsb");
            metadata.put("charCount", content.length());
            metadata.put("lineCount", DocumentParseUtils.countLines(content));
            return new ParsedDocument(DocumentParseUtils.titleFromFilename(context.getFilename(), "Excel Document"),
                    content, metadata);
        } catch (IOException ex) {
            throw new DocumentParseException("XLSB 文档解析失败", ex);
        }
    }

}
