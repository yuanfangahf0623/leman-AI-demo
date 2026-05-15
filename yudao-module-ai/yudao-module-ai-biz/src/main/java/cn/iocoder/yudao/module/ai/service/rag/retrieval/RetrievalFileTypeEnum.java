package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 当前知识库支持解析的文件类型分组。
 */
@Getter
@AllArgsConstructor
public enum RetrievalFileTypeEnum {

    TXT(Set.of("txt"), "纯文本"),
    MARKDOWN(Set.of("md"), "Markdown"),
    PDF(Set.of("pdf"), "PDF"),
    WORD(Set.of("doc", "docx", "wps"), "Word/WPS 文档"),
    EXCEL(Set.of("xls", "xlsx", "xlsb"), "Excel 表格"),
    POWERPOINT(Set.of("ppt", "pptx", "pptm"), "PowerPoint 演示文稿");

    private final Set<String> extensions;
    private final String name;

}
