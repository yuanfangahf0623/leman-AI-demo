package cn.iocoder.yudao.module.ai.framework.ocr;

import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 统一图片文字识别能力。
 *
 * <p>所有需要识别图片内容的业务都应依赖该接口，避免文档解析、发票审核等场景各自实现 OCR 策略。
 * 默认策略由 {@link HybridOcrService} 编排：先调用本地 Tesseract，结果为空、疑似乱码或质量较低时再调用视觉模型兜底。</p>
 */
public interface ImageRecognitionService {

    /**
     * 当前图片识别服务是否可用。
     */
    boolean isEnabled();

    /**
     * 识别单张图片。
     *
     * @param image 图片对象
     * @param context 文档解析上下文
     * @return 识别结果
     * @throws OcrException 图片识别失败
     */
    default OcrResult recognizeImage(BufferedImage image, DocumentParseContext context) throws OcrException {
        return recognizeImages(List.of(image), context);
    }

    /**
     * 按顺序识别多张图片，并合并为纯文本。
     *
     * @param images 图片列表，顺序即输出文本顺序
     * @param context 文档解析上下文
     * @return 识别结果
     * @throws OcrException 图片识别失败
     */
    OcrResult recognizeImages(List<BufferedImage> images, DocumentParseContext context) throws OcrException;

}
