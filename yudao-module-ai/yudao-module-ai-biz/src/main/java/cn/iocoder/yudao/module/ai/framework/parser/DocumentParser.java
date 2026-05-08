package cn.iocoder.yudao.module.ai.framework.parser;

import java.io.InputStream;

/**
 * 文档解析器接口。
 *
 * <p>实现类只负责把指定格式的文件流解析成纯文本，不做 chunk 和 embedding。</p>
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持该文件。
     *
     * @param filename 文件名，仅用于后缀判断，不能写入日志
     * @param contentType 浏览器或上游传入的 MIME 类型，只能作为辅助判断
     * @return 是否支持
     */
    boolean supports(String filename, String contentType);

    /**
     * 解析文件内容。
     *
     * @param inputStream 文件输入流
     * @param context 解析上下文
     * @return 解析后的文档
     * @throws DocumentParseException 解析失败
     */
    ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException;

}
