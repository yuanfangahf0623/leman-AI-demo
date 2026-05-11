package cn.iocoder.yudao.module.ai.framework.ocr;

/**
 * OCR 识别异常。
 */
public class OcrException extends Exception {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }

}
