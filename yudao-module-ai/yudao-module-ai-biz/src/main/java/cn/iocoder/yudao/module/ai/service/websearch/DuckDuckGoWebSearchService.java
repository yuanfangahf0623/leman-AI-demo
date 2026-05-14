package cn.iocoder.yudao.module.ai.service.websearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DuckDuckGoWebSearchService implements WebSearchService {

    private static final String DUCKDUCKGO_SEARCH_URL = "https://duckduckgo.com/html/?q=";
    private static final String BING_SEARCH_URL = "https://www.bing.com/search?q=";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern RESULT_LINK_PATTERN = Pattern.compile(
            "<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RESULT_SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>|"
                    + "<div[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BING_RESULT_PATTERN = Pattern.compile(
            "<li[^>]+class=\"[^\"]*b_algo[^\"]*\"[^>]*>.*?<h2[^>]*>\\s*<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>\\s*</h2>(.*?)</li>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BING_SNIPPET_PATTERN = Pattern.compile(
            "<p[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<WebSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        long startNanos = System.nanoTime();
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        try {
            List<WebSearchResult> results = searchDuckDuckGo(encodedQuery, query.length(), limit, startNanos);
            if (results.isEmpty()) {
                results = searchBing(encodedQuery, query.length(), limit, startNanos);
            }
            log.info("AI web search completed, queryLength={}, resultCount={}, elapsedMs={}",
                    query.length(), results.size(), elapsedMillis(startNanos));
            return results;
        } catch (IOException ex) {
            log.warn("AI web search IO failed, queryLength={}, elapsedMs={}, reason={}",
                    query.length(), elapsedMillis(startNanos), ex.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("AI web search interrupted, queryLength={}, elapsedMs={}", query.length(), elapsedMillis(startNanos));
            return Collections.emptyList();
        } catch (RuntimeException ex) {
            log.warn("AI web search failed, queryLength={}, elapsedMs={}, reason={}",
                    query.length(), elapsedMillis(startNanos), ex.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private List<WebSearchResult> searchDuckDuckGo(String encodedQuery, int queryLength, int limit, long startNanos)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendGet(DUCKDUCKGO_SEARCH_URL + encodedQuery);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("AI web search endpoint failed, engine=duckduckgo, statusCode={}, queryLength={}, elapsedMs={}",
                    response.statusCode(), queryLength, elapsedMillis(startNanos));
            return Collections.emptyList();
        }
        List<WebSearchResult> results = parseDuckDuckGoResults(response.body(), limit);
        log.info("AI web search endpoint completed, engine=duckduckgo, queryLength={}, resultCount={}, elapsedMs={}",
                queryLength, results.size(), elapsedMillis(startNanos));
        return results;
    }

    private List<WebSearchResult> searchBing(String encodedQuery, int queryLength, int limit, long startNanos)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendGet(BING_SEARCH_URL + encodedQuery);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("AI web search endpoint failed, engine=bing, statusCode={}, queryLength={}, elapsedMs={}",
                    response.statusCode(), queryLength, elapsedMillis(startNanos));
            return Collections.emptyList();
        }
        List<WebSearchResult> results = parseBingResults(response.body(), limit);
        log.info("AI web search endpoint completed, engine=bing, queryLength={}, resultCount={}, elapsedMs={}",
                queryLength, results.size(), elapsedMillis(startNanos));
        return results;
    }

    private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private List<WebSearchResult> parseDuckDuckGoResults(String html, int limit) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Map<String, WebSearchResult> results = new LinkedHashMap<>();
        Matcher matcher = RESULT_LINK_PATTERN.matcher(html);
        while (matcher.find() && results.size() < limit) {
            String url = normalizeUrl(matcher.group(1));
            String title = normalizeText(matcher.group(2));
            if (url == null || title.isBlank()) {
                continue;
            }
            String snippet = extractSnippet(html, matcher.end());
            results.putIfAbsent(url, WebSearchResult.builder()
                    .title(title)
                    .url(url)
                    .snippet(snippet)
                    .build());
        }
        return new ArrayList<>(results.values());
    }

    private List<WebSearchResult> parseBingResults(String html, int limit) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Map<String, WebSearchResult> results = new LinkedHashMap<>();
        Matcher matcher = BING_RESULT_PATTERN.matcher(html);
        while (matcher.find() && results.size() < limit) {
            String url = normalizeUrl(matcher.group(1));
            String title = normalizeText(matcher.group(2));
            if (url == null || title.isBlank()) {
                continue;
            }
            String snippet = extractBingSnippet(matcher.group(3));
            results.putIfAbsent(url, WebSearchResult.builder()
                    .title(title)
                    .url(url)
                    .snippet(snippet)
                    .build());
        }
        return new ArrayList<>(results.values());
    }

    private String extractBingSnippet(String resultHtml) {
        if (resultHtml == null || resultHtml.isBlank()) {
            return "";
        }
        Matcher matcher = BING_SNIPPET_PATTERN.matcher(resultHtml);
        if (!matcher.find()) {
            return "";
        }
        return normalizeText(matcher.group(1));
    }

    private String extractSnippet(String html, int startIndex) {
        int endIndex = Math.min(html.length(), startIndex + 1800);
        Matcher matcher = RESULT_SNIPPET_PATTERN.matcher(html.substring(startIndex, endIndex));
        if (!matcher.find()) {
            return "";
        }
        String snippet = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return normalizeText(snippet);
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String url = HtmlUtils.htmlUnescape(rawUrl.trim());
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        String decodedUddg = extractDuckDuckGoTarget(url);
        if (decodedUddg != null) {
            url = decodedUddg;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }
        if (url.contains("duckduckgo.com/y.js") || url.contains("duckduckgo.com/html")) {
            return null;
        }
        return url;
    }

    private String extractDuckDuckGoTarget(String url) {
        int start = url.indexOf("uddg=");
        if (start < 0) {
            return null;
        }
        String encoded = url.substring(start + "uddg=".length());
        int end = encoded.indexOf('&');
        if (end >= 0) {
            encoded = encoded.substring(0, end);
        }
        if (encoded.isBlank()) {
            return null;
        }
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String normalizeText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return HtmlUtils.htmlUnescape(rawText.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
