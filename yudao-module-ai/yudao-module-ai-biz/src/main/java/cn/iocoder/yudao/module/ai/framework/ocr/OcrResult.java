package cn.iocoder.yudao.module.ai.framework.ocr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OCR 识别结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {

    /**
     * OCR 提取出的纯文本。
     */
    private String content;

    /**
     * OCR 元数据，例如识别页数、语言、耗时等。
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
