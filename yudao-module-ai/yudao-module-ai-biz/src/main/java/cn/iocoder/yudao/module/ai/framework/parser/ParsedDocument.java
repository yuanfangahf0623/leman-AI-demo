package cn.iocoder.yudao.module.ai.framework.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档解析结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 纯文本内容。
     */
    private String content;

    /**
     * 基础元数据，例如文件名、页数、行数、表格数量等。
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
