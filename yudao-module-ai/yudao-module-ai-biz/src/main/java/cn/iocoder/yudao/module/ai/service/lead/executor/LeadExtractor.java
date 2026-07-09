package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawledPage;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ExtractionResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts public business contact fields from crawled pages.
 */
public class LeadExtractor {

    public static final String EMAIL_PURCHASING = "采购邮箱";
    public static final String EMAIL_DECISION_MAKER = "负责人邮箱";
    public static final String EMAIL_SALES = "销售/批发邮箱";
    public static final String EMAIL_GENERIC = "通用邮箱";

    private static final Pattern EMAIL_RE = Pattern.compile("\\b[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_RE = Pattern.compile("(?:\\+\\d{1,3}[\\s().-]*)?(?:\\d[\\s().-]*){7,}\\d");
    private static final List<String> BLOCKED_EMAIL_PREFIXES = List.of(
            "noreply", "no-reply", "do-not-reply", "donotreply", "privacy", "datenschutz", "dpo",
            "abuse", "postmaster");
    private static final Map<String, List<String>> PRODUCT_KEYWORDS = Map.ofEntries(
            Map.entry("wall mounted washing machine", List.of("wall mounted washing machine", "wall-mounted washing machine", "wall mounted washer")),
            Map.entry("mini washing machine", List.of("mini washing machine", "compact washing machine", "compact washer")),
            Map.entry("small appliance", List.of("small appliance", "small appliances", "home appliances", "bathroom appliances")),
            Map.entry("baby products", List.of("baby products", "baby shop", "baby goods", "baby care")),
            Map.entry("nursery products", List.of("nursery products", "nursery furniture", "child care", "children products")),
            Map.entry("yacht equipment", List.of("yacht equipment", "yacht accessories", "yacht interior")),
            Map.entry("marine accessories", List.of("marine accessories", "marine supplies", "boat equipment", "boat galley")),
            Map.entry("camper van accessories", List.of("camper van accessories", "camper accessories", "van accessories")),
            Map.entry("RV equipment", List.of("rv equipment", "caravan accessories", "camping appliances")));

    public ExtractionResult extractSite(List<CrawledPage> pages, String domain) {
        List<CrawledPage> safePages = pages == null ? List.of() : pages;
        String allText = String.join(" ", safePages.stream().map(CrawledPage::getText).toList());
        Map<String, String> emailSources = new LinkedHashMap<>();
        List<String> emails = new ArrayList<>();
        String contactPage = null;
        String aboutPage = null;
        for (CrawledPage page : safePages) {
            String content = (page.getHtml() == null ? "" : page.getHtml()) + " " + (page.getText() == null ? "" : page.getText());
            for (String email : extractEmails(content)) {
                if (!emails.contains(email)) {
                    emails.add(email);
                    emailSources.put(email, page.getUrl());
                }
            }
            if (contactPage == null && "contact".equals(page.getPageType())) {
                contactPage = page.getUrl();
            }
            if (aboutPage == null && "about".equals(page.getPageType())) {
                aboutPage = page.getUrl();
            }
        }
        String bestEmail = chooseBestEmail(emails);
        String sourceUrl = "";
        if (bestEmail != null) {
            sourceUrl = emailSources.getOrDefault(bestEmail, "");
        } else if (contactPage != null) {
            sourceUrl = contactPage;
        } else if (!safePages.isEmpty()) {
            sourceUrl = safePages.get(0).getUrl();
        }
        return ExtractionResult.builder()
                .companyName(extractCompanyName(safePages, domain))
                .emails(emails)
                .emailSources(emailSources)
                .bestEmail(bestEmail)
                .emailType(classifyEmailType(bestEmail))
                .phone(extractPhone(allText))
                .contactPage(contactPage)
                .aboutPage(aboutPage)
                .mainProducts(extractMainProducts(allText))
                .sourceUrl(sourceUrl)
                .build();
    }

    public List<String> extractEmails(String value) {
        String normalized = deobfuscateText(value);
        Matcher matcher = EMAIL_RE.matcher(normalized);
        List<String> results = new ArrayList<>();
        while (matcher.find()) {
            String email = matcher.group().replaceAll("^[.,;:()\\[\\]{}<>]+|[.,;:()\\[\\]{}<>]+$", "")
                    .toLowerCase(Locale.ROOT);
            if (!isBlockedEmail(email) && !results.contains(email)) {
                results.add(email);
            }
        }
        return results;
    }

    public String classifyEmailType(String emailAddress) {
        if (!LeadAgentUtils.hasText(emailAddress) || !emailAddress.contains("@")) {
            return null;
        }
        String local = emailAddress.split("@", 2)[0].toLowerCase(Locale.ROOT);
        if (List.of("purchasing", "procurement", "buying", "purchase", "buyer").contains(local)) {
            return EMAIL_PURCHASING;
        }
        if (List.of("owner", "ceo", "founder", "director", "manager", "managingdirector").contains(local)) {
            return EMAIL_DECISION_MAKER;
        }
        if (List.of("sales", "wholesale", "b2b", "export", "dealer", "distribution").contains(local)) {
            return EMAIL_SALES;
        }
        if (List.of("info", "contact", "service", "office", "hello", "support").contains(local)) {
            return EMAIL_GENERIC;
        }
        if (local.matches("[a-z]{2,}\\.[a-z]{2,}") || local.matches("[a-z][a-z\\-]{2,}")) {
            return EMAIL_DECISION_MAKER;
        }
        return EMAIL_GENERIC;
    }

    private String deobfuscateText(String value) {
        String text = value == null ? "" : value;
        text = text.replaceAll("(?i)\\s*(?:\\[|\\(|\\{)\\s*at\\s*(?:\\]|\\)|\\})\\s*", "@");
        text = text.replaceAll("(?i)\\s+at\\s+", "@");
        text = text.replaceAll("(?i)\\s*(?:\\[|\\(|\\{)\\s*dot\\s*(?:\\]|\\)|\\})\\s*", ".");
        text = text.replaceAll("(?i)\\s+dot\\s+", ".");
        text = text.replaceAll("\\s*@\\s*", "@");
        text = text.replaceAll("(?<=\\w)\\s*\\.\\s*(?=\\w)", ".");
        return text;
    }

    private boolean isBlockedEmail(String emailAddress) {
        String local = emailAddress.split("@", 2)[0].toLowerCase(Locale.ROOT);
        return BLOCKED_EMAIL_PREFIXES.stream().anyMatch(local::startsWith);
    }

    private String chooseBestEmail(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
                .min(Comparator.comparingInt(this::emailPriority).thenComparing(String::valueOf))
                .orElse(null);
    }

    private int emailPriority(String email) {
        String type = classifyEmailType(email);
        if (EMAIL_PURCHASING.equals(type)) {
            return 1;
        }
        if (EMAIL_DECISION_MAKER.equals(type)) {
            return 2;
        }
        if (EMAIL_SALES.equals(type)) {
            return 3;
        }
        return 4;
    }

    private String extractPhone(String value) {
        Matcher matcher = PHONE_RE.matcher(value == null ? "" : value);
        String best = null;
        while (matcher.find()) {
            String candidate = LeadAgentUtils.compactWhitespace(matcher.group());
            String digits = candidate.replaceAll("\\D", "");
            if (digits.length() >= 7 && digits.length() <= 16 && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best;
    }

    private String extractCompanyName(List<CrawledPage> pages, String domain) {
        for (CrawledPage page : pages) {
            if (!LeadAgentUtils.hasText(page.getHtml())) {
                continue;
            }
            Document document = Jsoup.parse(page.getHtml(), page.getUrl());
            Element siteName = document.selectFirst("meta[property=og:site_name]");
            if (siteName != null && LeadAgentUtils.hasText(siteName.attr("content"))) {
                return LeadAgentUtils.compactWhitespace(siteName.attr("content"));
            }
            if (LeadAgentUtils.hasText(document.title())) {
                return cleanCompanyName(document.title());
            }
            Element h1 = document.selectFirst("h1");
            if (h1 != null && LeadAgentUtils.hasText(h1.text())) {
                return cleanCompanyName(h1.text());
            }
        }
        if (LeadAgentUtils.hasText(domain)) {
            String stem = domain.split("\\.", 2)[0].replace("-", " ");
            return Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
        }
        return null;
    }

    private String cleanCompanyName(String value) {
        String cleaned = LeadAgentUtils.compactWhitespace(value);
        cleaned = cleaned.split("\\s+[-|]\\s+", 2)[0];
        cleaned = cleaned.replaceAll("(?i)\\b(Home|Contact|About|Impressum|Official Site)\\b", "");
        return LeadAgentUtils.compactWhitespace(cleaned);
    }

    private List<String> extractMainProducts(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> products = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : PRODUCT_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) {
                products.add(entry.getKey());
            }
        }
        return products;
    }

}
