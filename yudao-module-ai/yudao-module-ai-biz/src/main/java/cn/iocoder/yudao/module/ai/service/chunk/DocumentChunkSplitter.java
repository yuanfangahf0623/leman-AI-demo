package cn.iocoder.yudao.module.ai.service.chunk;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_CHUNK_CONFIG_INVALID;

/**
 * 文档切片器。
 *
 * <p>优先按空行分隔的段落组织 chunk；单个段落超过 chunkSize 时，再使用长度滑窗切分。</p>
 */
@Component
public class DocumentChunkSplitter {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    public List<String> split(String content, Integer chunkSize, Integer chunkOverlap) {
        validateConfig(chunkSize, chunkOverlap);
        String normalizedContent = normalizeContent(content);
        if (normalizedContent.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : splitParagraphs(normalizedContent)) {
            if (paragraph.length() > chunkSize) {
                flushCurrent(chunks, current);
                List<String> paragraphChunks = splitLongParagraph(paragraph, chunkSize, chunkOverlap);
                chunks.addAll(paragraphChunks);
                continue;
            }
            appendParagraph(chunks, current, paragraph, chunkSize, chunkOverlap);
        }
        flushCurrent(chunks, current);
        return chunks;
    }

    private void validateConfig(Integer chunkSize, Integer chunkOverlap) {
        if (chunkSize == null || chunkSize <= 0) {
            throw new ServiceException(DOCUMENT_CHUNK_CONFIG_INVALID, "chunkSize 必须大于 0");
        }
        if (chunkOverlap == null || chunkOverlap < 0) {
            throw new ServiceException(DOCUMENT_CHUNK_CONFIG_INVALID, "chunkOverlap 不能小于 0");
        }
        if (chunkOverlap >= chunkSize) {
            throw new ServiceException(DOCUMENT_CHUNK_CONFIG_INVALID, "chunkOverlap 必须小于 chunkSize");
        }
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalizedParagraph = paragraph.trim();
            if (!normalizedParagraph.isBlank()) {
                paragraphs.add(normalizedParagraph);
            }
        }
        return paragraphs;
    }

    private List<String> splitLongParagraph(String paragraph, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = chunkSize - chunkOverlap;
        while (start < paragraph.length()) {
            int end = Math.min(start + chunkSize, paragraph.length());
            String chunk = paragraph.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == paragraph.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private void appendParagraph(List<String> chunks, StringBuilder current, String paragraph,
                                 int chunkSize, int chunkOverlap) {
        if (current.length() == 0) {
            current.append(paragraph);
            return;
        }
        int nextLength = current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length();
        if (nextLength <= chunkSize) {
            current.append(PARAGRAPH_SEPARATOR).append(paragraph);
            return;
        }

        String lastChunk = current.toString();
        flushCurrent(chunks, current);
        appendOverlapTail(current, lastChunk, chunkOverlap);
        if (current.length() == 0) {
            current.append(paragraph);
            return;
        }
        nextLength = current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length();
        if (nextLength <= chunkSize) {
            current.append(PARAGRAPH_SEPARATOR).append(paragraph);
            return;
        }
        // 如果重叠尾部加上新段落会超过限制，则保留段落边界，放弃这一次跨段落 overlap。
        current.setLength(0);
        current.append(paragraph);
    }

    private void appendOverlapTail(StringBuilder current, String chunk, int chunkOverlap) {
        current.setLength(0);
        if (chunkOverlap <= 0 || chunk == null || chunk.isBlank()) {
            return;
        }
        int start = Math.max(0, chunk.length() - chunkOverlap);
        current.append(chunk.substring(start).trim());
    }

    private void flushCurrent(List<String> chunks, StringBuilder current) {
        String chunk = current.toString().trim();
        if (!chunk.isBlank()) {
            chunks.add(chunk);
        }
        current.setLength(0);
    }

}
