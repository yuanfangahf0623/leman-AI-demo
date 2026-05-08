package cn.iocoder.yudao.module.ai.framework.parser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文档解析工具方法。
 */
public final class DocumentParseUtils {

    private static final byte[] OLE2_MAGIC = new byte[] {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    private DocumentParseUtils() {
    }

    public static boolean hasExtension(String filename, Set<String> extensions) {
        return extensions.contains(extension(filename));
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean contentTypeContains(String contentType, String keyword) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public static String titleFromFilename(String filename, String defaultTitle) {
        if (filename == null || filename.isBlank()) {
            return defaultTitle;
        }
        String normalized = filename.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    public static Map<String, Object> baseMetadata(DocumentParseContext context, String parser, String format) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parser", parser);
        metadata.put("format", format);
        metadata.put("fileName", context.getFilename());
        metadata.put("documentId", context.getDocumentId());
        metadata.put("tenantId", context.getTenantId());
        metadata.put("knowledgeBaseId", context.getKnowledgeBaseId());
        return metadata;
    }

    public static int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public static boolean isZip(byte[] content) {
        return content.length >= 2 && content[0] == 'P' && content[1] == 'K';
    }

    public static boolean isOle2(byte[] content) {
        if (content.length < OLE2_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < OLE2_MAGIC.length; i++) {
            if (content[i] != OLE2_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

}
