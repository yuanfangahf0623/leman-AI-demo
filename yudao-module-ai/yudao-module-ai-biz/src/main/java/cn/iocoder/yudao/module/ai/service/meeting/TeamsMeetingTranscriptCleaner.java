package cn.iocoder.yudao.module.ai.service.meeting;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class TeamsMeetingTranscriptCleaner {

    private static final String TIMESTAMP_SEPARATOR = " --> ";

    public String clean(String rawTranscript, int maxChars) {
        if (!StringUtils.hasText(rawTranscript)) {
            return "";
        }
        String normalized = rawTranscript.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("WEBVTT")) {
            return limit(normalizePlainText(normalized), maxChars);
        }
        return limit(cleanVtt(normalized), maxChars);
    }

    private String cleanVtt(String content) {
        String[] lines = content.split("\n");
        List<String> output = new ArrayList<>();
        String currentTimestamp = null;
        StringBuilder currentText = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                flushCue(output, currentTimestamp, currentText);
                currentTimestamp = null;
                currentText = new StringBuilder();
                continue;
            }
            if ("WEBVTT".equalsIgnoreCase(line) || line.startsWith("NOTE")
                    || line.startsWith("STYLE") || line.matches("\\d+")) {
                continue;
            }
            if (line.contains(TIMESTAMP_SEPARATOR)) {
                flushCue(output, currentTimestamp, currentText);
                currentText = new StringBuilder();
                currentTimestamp = normalizeTimestamp(line);
                continue;
            }
            String text = stripTags(line);
            if (!text.isBlank()) {
                if (!currentText.isEmpty()) {
                    currentText.append(' ');
                }
                currentText.append(text);
            }
        }
        flushCue(output, currentTimestamp, currentText);
        return String.join("\n", output);
    }

    private void flushCue(List<String> output, String timestamp, StringBuilder text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String line = text.toString().trim();
        if (!line.isBlank()) {
            output.add((timestamp == null ? "" : timestamp + " ") + line);
        }
    }

    private String normalizeTimestamp(String line) {
        String[] parts = line.split(TIMESTAMP_SEPARATOR);
        if (parts.length < 2) {
            return "";
        }
        String start = parts[0].trim();
        String end = parts[1].split("\\s+")[0].trim();
        return "[" + start + " - " + end + "]";
    }

    private String normalizePlainText(String content) {
        StringBuilder builder = new StringBuilder();
        for (String rawLine : content.split("\n")) {
            String line = stripTags(rawLine).trim();
            if (!line.isBlank()) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String stripTags(String value) {
        return value.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private String limit(String text, int maxChars) {
        if (text == null || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

}
