package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_PARSE_FAILED;

/**
 * Parses raw EML/MIME content into normalized EmailDTO.
 */
@Slf4j
@Service
public class EmlParseService {

    private static final int MAX_BODY_CHARS = 50_000;
    private static final int MAX_ATTACHMENT_TEXT_CHARS = 20_000;
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "<(script|style)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_BREAK_PATTERN = Pattern.compile("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    public EmailDTO parse(EmailRawDTO raw) {
        if (raw == null || !StringUtils.hasText(raw.getRawMime())) {
            throw new ServiceException(RFQ_EMAIL_PARSE_FAILED, "Email raw MIME is empty");
        }
        try {
            MimeMessage message = toMimeMessage(raw.getRawMime());
            BodyCollector collector = new BodyCollector();
            collectPart(message, collector);
            String bodyText = firstText(normalizeText(collector.plainText.toString()),
                    normalizeText(cleanHtml(collector.htmlText.toString())));
            return EmailDTO.builder()
                    .from(firstText(raw.getFrom(), addressesToText(message.getFrom())))
                    .to(firstText(raw.getTo(), addressesToText(message.getRecipients(Message.RecipientType.TO))))
                    .subject(firstText(raw.getSubject(), decodeText(message.getSubject())))
                    .bodyText(limit(bodyText, MAX_BODY_CHARS))
                    .attachments(collector.attachments)
                    .receivedTime(firstDateTime(raw.getReceivedTime(), toLocalDateTime(message.getReceivedDate())))
                    .build();
        } catch (Exception ex) {
            log.warn("EML parse failed, account={}, messageId={}, errorType={}",
                    raw.getAccount(), raw.getMessageId(), ex.getClass().getSimpleName());
            throw new ServiceException(RFQ_EMAIL_PARSE_FAILED, "Email parse failed");
        }
    }

    private MimeMessage toMimeMessage(String rawMime) throws Exception {
        Session session = Session.getInstance(new Properties());
        byte[] bytes = rawMime.getBytes(StandardCharsets.ISO_8859_1);
        return new MimeMessage(session, new ByteArrayInputStream(bytes));
    }

    private void collectPart(Part part, BodyCollector collector) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                collectPart(bodyPart, collector);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object nested = part.getContent();
            if (nested instanceof Part nestedPart) {
                collectPart(nestedPart, collector);
            }
            return;
        }
        if (isAttachment(part)) {
            collectAttachment(part, collector);
            return;
        }
        if (part.isMimeType("text/plain")) {
            appendText(collector.plainText, part);
            return;
        }
        if (part.isMimeType("text/html")) {
            appendText(collector.htmlText, part);
        }
    }

    private void appendText(StringBuilder builder, Part part) throws Exception {
        String text = part.getContent() instanceof String content ? content : "";
        if (StringUtils.hasText(text)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(text);
        }
    }

    private void collectAttachment(Part part, BodyCollector collector) throws Exception {
        String fileName = decodeText(part.getFileName());
        String contentType = part.getContentType();
        byte[] content = part.getInputStream().readAllBytes();
        String text = extractAttachmentText(fileName, contentType, content);
        collector.attachments.add(EmailDTO.Attachment.builder()
                .fileName(fileName)
                .contentType(contentType)
                .size((long) content.length)
                .text(limit(normalizeText(text), MAX_ATTACHMENT_TEXT_CHARS))
                .content(content)
                .build());
    }

    private String extractAttachmentText(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            if (isPdf(fileName, contentType)) {
                return extractPdfText(content);
            }
            if (isExcel(fileName, contentType)) {
                return extractExcelText(content);
            }
        } catch (Exception ex) {
            log.warn("Email attachment text extract failed, fileName={}, contentType={}, errorType={}",
                    sanitizeFileNameForLog(fileName), contentType, ex.getClass().getSimpleName());
        }
        return "";
    }

    private String extractPdfText(byte[] content) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractExcelText(byte[] content) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder builder = new StringBuilder();
            for (Sheet sheet : workbook) {
                if (builder.length() > MAX_ATTACHMENT_TEXT_CHARS) {
                    break;
                }
                builder.append("# ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    if (builder.length() > MAX_ATTACHMENT_TEXT_CHARS) {
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        cells.add(value == null ? "" : value.trim());
                    }
                    if (!cells.isEmpty()) {
                        builder.append(String.join("\t", cells)).append('\n');
                    }
                }
            }
            return builder.toString();
        }
    }

    private boolean isAttachment(Part part) throws Exception {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        return Part.INLINE.equalsIgnoreCase(disposition) && StringUtils.hasText(part.getFileName())
                || StringUtils.hasText(part.getFileName());
    }

    private boolean isPdf(String fileName, String contentType) {
        return hasExtension(fileName, "pdf")
                || containsIgnoreCase(contentType, "application/pdf");
    }

    private boolean isExcel(String fileName, String contentType) {
        return hasExtension(fileName, "xls") || hasExtension(fileName, "xlsx")
                || containsIgnoreCase(contentType, "application/vnd.ms-excel")
                || containsIgnoreCase(contentType, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private boolean hasExtension(String fileName, String extension) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    private boolean containsIgnoreCase(String text, String target) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    private String cleanHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        text = HTML_BREAK_PATTERN.matcher(text).replaceAll("\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        return HtmlUtils.htmlUnescape(text);
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return repairLatin1MojibakeIfNeeded(normalized);
    }

    private String repairLatin1MojibakeIfNeeded(String text) {
        if (!StringUtils.hasText(text) || countMojibakeMarkers(text) == 0) {
            return text;
        }
        String repaired = new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return countCjkChars(repaired) > countCjkChars(text) ? repaired : text;
    }

    private int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; text != null && i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    private int countMojibakeMarkers(String text) {
        int count = 0;
        String markers = "\u951F\uFFFD\u00C3\u00C2\u00E4\u00B8\u00E5\u203A\u00BD\u00E6\u2013\u2021";
        for (int i = 0; text != null && i < text.length(); i++) {
            if (markers.indexOf(text.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }

    private String addressesToText(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address != null) {
                values.add(address.toString());
            }
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String decodeText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        try {
            return MimeUtility.decodeText(text);
        } catch (Exception ignored) {
            return text;
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private LocalDateTime firstDateTime(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String sanitizeFileNameForLog(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.length() <= 128 ? fileName : fileName.substring(0, 128);
    }

    private static class BodyCollector {

        private final StringBuilder plainText = new StringBuilder();

        private final StringBuilder htmlText = new StringBuilder();

        private final List<EmailDTO.Attachment> attachments = new ArrayList<>();

    }

}
