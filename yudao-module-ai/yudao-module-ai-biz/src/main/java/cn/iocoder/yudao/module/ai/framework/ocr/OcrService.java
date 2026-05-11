package cn.iocoder.yudao.module.ai.framework.ocr;

import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * OCR 识别服务抽象。
 *
 * <p>业务解析层只依赖该接口，后续可替换为云 OCR、私有化 OCR 或队列异步 OCR。</p>
 */
public interface OcrService {

    /**
     * 当前 OCR 服务是否可用。
     */
    boolean isEnabled();

    /**
     * 对 PDF 文档执行 OCR 识别。
     *
     * @param document PDF 文档对象
     * @param context 文档解析上下文
     * @return OCR 识别结果
     * @throws OcrException OCR 识别失败
     */
    OcrResult recognizePdf(PDDocument document, DocumentParseContext context) throws OcrException;

}
