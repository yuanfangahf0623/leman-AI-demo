package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawlResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawledPage;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Respectful public web-page crawler for lead collection.
 */
@Slf4j
public class LeadWebCrawler {

    private static final Map<String, List<String>> PAGE_KEYWORDS = Map.of(
            "contact", List.of("contact", "kontakt", "contatti", "contacto", "contacts"),
            "about", List.of("about", "about us", "company", "who we are", "ueber", "über", "chi siamo"),
            "imprint", List.of("imprint", "impressum", "legal notice", "mentions legales", "aviso legal"),
            "privacy", List.of("privacy", "datenschutz", "data protection"),
            "wholesale", List.of("wholesale", "b2b", "dealer", "dealers", "distribution", "distributor"),
            "trade", List.of("haendler", "händler", "vertrieb", "revendeur", "distribuidores", "dystrybucja"));
    private static final Map<String, Integer> PAGE_PRIORITY = Map.of(
            "contact", 1, "wholesale", 2, "trade", 2, "imprint", 3, "about", 4, "privacy", 5, "home", 9);

    private final int maxPagesPerSite;
    private final int timeoutSeconds;
    private final Map<String, String> mockPages;
    private final Map<String, Long> lastRequestMillisByDomain = new HashMap<>();

    public LeadWebCrawler(int maxPagesPerSite, int timeoutSeconds, Map<String, String> mockPages) {
        this.maxPagesPerSite = Math.max(1, Math.min(10, maxPagesPerSite));
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.mockPages = mockPages == null ? Map.of() : mockPages;
    }

    public CrawlResult crawlSite(String startUrl) {
        String normalizedStart = LeadAgentUtils.normalizeUrl(startUrl);
        if (!LeadAgentUtils.hasText(normalizedStart)) {
            return CrawlResult.builder().startUrl(startUrl).domain("").errors(new ArrayList<>(List.of("Invalid start URL"))).build();
        }

        String domain = LeadAgentUtils.extractDomain(normalizedStart);
        CrawlResult result = CrawlResult.builder()
                .startUrl(normalizedStart)
                .finalUrl(normalizedStart)
                .domain(domain)
                .pages(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> queued = new LinkedHashSet<>();
        Set<String> fetched = new LinkedHashSet<>();
        queue.add(normalizedStart);
        queued.add(normalizedStart);

        while (!queue.isEmpty() && result.getPages().size() < maxPagesPerSite) {
            String url = queue.poll();
            if (fetched.contains(url) || LeadAgentUtils.shouldSkipUrl(url)) {
                continue;
            }
            fetched.add(url);
            CrawledPage page = fetchPage(url);
            result.getPages().add(page);
            if (LeadAgentUtils.hasText(page.getError())) {
                result.getErrors().add(url + ": " + page.getError());
                continue;
            }
            for (String nextUrl : discoverRelevantLinks(page.getHtml(), url, domain)) {
                if (queued.size() >= maxPagesPerSite + 8) {
                    break;
                }
                if (!queued.contains(nextUrl) && !fetched.contains(nextUrl)) {
                    queued.add(nextUrl);
                    queue.add(nextUrl);
                }
            }
            List<String> sortedQueue = queue.stream()
                    .sorted(Comparator.comparingInt(item -> PAGE_PRIORITY.getOrDefault(guessPageType(item), 8)))
                    .toList();
            queue.clear();
            queue.addAll(sortedQueue);
        }
        return result;
    }

    private CrawledPage fetchPage(String url) {
        String html = mockPages.get(url);
        String error = null;
        if (html == null) {
            rateLimit(LeadAgentUtils.extractDomain(url));
            try {
                Document document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 LeadAgentMVP/1.0 (public B2B contact research; respectful crawling)")
                        .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
                        .followRedirects(true)
                        .ignoreContentType(true)
                        .get();
                html = document.outerHtml();
            } catch (IOException | IllegalArgumentException ex) {
                error = ex.getMessage();
                html = "";
            }
        }
        String title = null;
        String text = "";
        if (LeadAgentUtils.hasText(html)) {
            Document document = Jsoup.parse(html, url);
            title = LeadAgentUtils.compactWhitespace(document.title());
            text = LeadAgentUtils.compactWhitespace(document.text());
        }
        return CrawledPage.builder()
                .url(url)
                .html(html)
                .text(text)
                .title(title)
                .pageType(guessPageType(url))
                .error(error)
                .build();
    }

    private void rateLimit(String domain) {
        if (!LeadAgentUtils.hasText(domain)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRequestMillisByDomain.get(domain);
        if (last != null) {
            long waitMillis = 500L - (now - last);
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastRequestMillisByDomain.put(domain, System.currentTimeMillis());
    }

    private List<String> discoverRelevantLinks(String html, String baseUrl, String domain) {
        if (!LeadAgentUtils.hasText(html)) {
            return List.of();
        }
        Document document = Jsoup.parse(html, baseUrl);
        List<String> candidates = new ArrayList<>();
        for (Element link : document.select("a[href]")) {
            String href = LeadAgentUtils.normalizeUrl(link.attr("href"), baseUrl);
            if (!LeadAgentUtils.hasText(href) || LeadAgentUtils.shouldSkipUrl(href)
                    || !domain.equals(LeadAgentUtils.extractDomain(href))) {
                continue;
            }
            String pageType = guessPageType(href + " " + link.text());
            if ("home".equals(pageType)) {
                continue;
            }
            candidates.add(href);
        }
        return candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt(item -> PAGE_PRIORITY.getOrDefault(guessPageType(item), 8)))
                .limit(maxPagesPerSite * 2L)
                .toList();
    }

    private String guessPageType(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        String normalized = lower.replace("https://", "").replace("http://", "");
        if (!normalized.contains("/") || normalized.endsWith("/") || normalized.endsWith("/index.html")) {
            return "home";
        }
        for (Map.Entry<String, List<String>> entry : PAGE_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) {
                return entry.getKey();
            }
        }
        return "other";
    }

}
