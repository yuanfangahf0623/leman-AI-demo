package cn.iocoder.yudao.module.ai.service.lead.executor;

import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared utility methods for lead-agent execution.
 */
public final class LeadAgentUtils {

    public static final String DEFAULT_COMPLIANCE_NOTE = "Collected only from public business web pages; "
            + "email source URL retained for manual verification; no automated email sending.";
    public static final Set<String> TARGET_COUNTRIES = Set.of(
            "Germany", "France", "Italy", "Spain", "Netherlands", "Belgium", "Austria", "Switzerland",
            "Poland", "Czech Republic", "Portugal", "Greece", "Croatia", "Sweden", "Norway", "Denmark",
            "Finland", "Ireland", "United Kingdom", "Romania", "Hungary", "Slovakia", "Slovenia",
            "Estonia", "Latvia", "Lithuania", "Luxembourg", "Bulgaria");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> BLOCKED_DOMAIN_KEYWORDS = List.of(
            "amazon", "ebay", "aliexpress", "alibaba", "temu", "ubuy", "apple", "sciencedirect",
            "snsinsider", "noon", "fruugo", "kiwicollection", "fitchratings", "facebook", "instagram",
            "linkedin", "youtube", "youtu", "tiktok", "pinterest", "wikipedia", "google", "bing",
            "reddit", "quora", "trustpilot", "tripadvisor", "yelp");
    private static final List<String> SKIPPED_EXTENSIONS = List.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".mp4", ".mov", ".avi", ".zip", ".rar");

    private LeadAgentUtils() {
    }

    public static String currentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static String normalizeUrl(String value) {
        return normalizeUrl(value, null);
    }

    public static String normalizeUrl(String value, String baseUrl) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = baseUrl == null ? new URI(value.trim()) : new URI(baseUrl).resolve(value.trim());
            if (uri.getScheme() == null) {
                uri = new URI("https://" + value.trim());
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : "?" + uri.getRawQuery();
            String normalized = scheme + "://" + host + path + query;
            if (normalized.endsWith("/") && path.length() <= 1 && query.isBlank()) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    public static String extractDomain(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return "";
        }
        try {
            String host = new URI(normalized).getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    public static boolean shouldSkipUrl(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return SKIPPED_EXTENSIONS.stream().anyMatch(lower::endsWith) || isBlockedDomain(extractDomain(lower));
    }

    public static boolean isBlockedDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        String lower = domain.toLowerCase(Locale.ROOT);
        return BLOCKED_DOMAIN_KEYWORDS.stream().anyMatch(lower::contains);
    }

    public static String compactWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(HtmlUtils.htmlUnescape(value)).replaceAll(" ").trim();
    }

    public static List<String> uniquePreserveOrder(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String item = value.trim();
            if (seen.add(item.toLowerCase(Locale.ROOT))) {
                result.add(item);
            }
        }
        return result;
    }

    public static String gradeFromScore(int score) {
        if (score >= 80) {
            return "A";
        }
        if (score >= 60) {
            return "B";
        }
        if (score >= 40) {
            return "C";
        }
        return "D";
    }

    public static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

}
