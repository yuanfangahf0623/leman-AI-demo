package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SearchEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bing and SerpAPI based search provider.
 */
@Slf4j
public class LeadHttpSearchProvider implements LeadSearchProvider {

    private static final List<String> SEARCH_QUERY_EXCLUSIONS = List.of(
            "amazon", "ebay", "alibaba", "aliexpress", "temu", "ubuy", "apple", "app", "apps",
            "facebook", "instagram", "linkedin", "youtube", "pinterest", "wikipedia", "reddit",
            "quora", "trustpilot", "tripadvisor", "directory", "report", "reports", "research",
            "article", "study", "news", "blog", "forum", "review", "reviews", "faq", "warranty",
            "manual", "support", "terms", "conditions", "jobs", "career");

    private final String providerName;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public LeadHttpSearchProvider(String providerName, String apiKey) {
        this.providerName = providerName;
        this.apiKey = apiKey;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public List<String> search(String keyword, String country, int maxResults) {
        List<SearchEvidence> evidence = searchEvidence(buildCustomerSearchQuery(keyword, country), null, maxResults);
        return evidence.stream()
                .map(SearchEvidence::getUrl)
                .filter(url -> !LeadAgentUtils.shouldSkipUrl(url))
                .distinct()
                .limit(maxResults)
                .toList();
    }

    @Override
    public List<SearchEvidence> searchEvidence(String query, String country, int maxResults) {
        if (!LeadAgentUtils.hasText(apiKey) || !LeadAgentUtils.hasText(query) || maxResults <= 0) {
            return List.of();
        }
        String fullQuery = (query + " " + (country == null ? "" : country)).trim();
        try {
            if ("bing".equals(providerName)) {
                return searchBing(fullQuery, maxResults);
            }
            if ("serpapi".equals(providerName)) {
                return searchSerpApi(fullQuery, maxResults);
            }
        } catch (Exception ex) {
            log.warn("Lead search call failed, provider={}, queryLength={}, reason={}",
                    providerName, fullQuery.length(), ex.getClass().getSimpleName());
        }
        return List.of();
    }

    private List<SearchEvidence> searchBing(String query, int maxResults) throws IOException, InterruptedException {
        String url = "https://api.bing.microsoft.com/v7.0/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + Math.min(maxResults, 50) + "&responseFilter=Webpages";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }
        JsonNode items = objectMapper.readTree(response.body()).path("webPages").path("value");
        List<SearchEvidence> results = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                addEvidence(results, item.path("url").asText(), item.path("name").asText(), item.path("snippet").asText());
                if (results.size() >= maxResults) {
                    break;
                }
            }
        }
        return results;
    }

    private List<SearchEvidence> searchSerpApi(String query, int maxResults) throws IOException, InterruptedException {
        String url = "https://serpapi.com/search.json?engine=google&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&num=" + Math.min(maxResults, 100);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }
        JsonNode items = objectMapper.readTree(response.body()).path("organic_results");
        List<SearchEvidence> results = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                addEvidence(results, item.path("link").asText(), item.path("title").asText(), item.path("snippet").asText());
                if (results.size() >= maxResults) {
                    break;
                }
            }
        }
        return results;
    }

    private void addEvidence(List<SearchEvidence> results, String rawUrl, String title, String snippet) {
        String url = LeadAgentUtils.normalizeUrl(rawUrl);
        if (!LeadAgentUtils.hasText(url)) {
            return;
        }
        results.add(SearchEvidence.builder()
                .url(url)
                .title(title == null ? "" : title)
                .snippet(snippet == null ? "" : snippet)
                .build());
    }

    private String buildCustomerSearchQuery(String keyword, String country) {
        List<String> parts = new ArrayList<>();
        if (LeadAgentUtils.hasText(keyword)) {
            parts.add(keyword.trim());
        }
        if (LeadAgentUtils.hasText(country)) {
            parts.add(country.trim());
        }
        String lower = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        if (!lower.contains("contact") && !lower.contains("email")) {
            parts.add("contact email");
        }
        parts.add(String.join(" ", SEARCH_QUERY_EXCLUSIONS.stream().map(item -> "-" + item).toList()));
        return String.join(" ", parts).trim();
    }

}
