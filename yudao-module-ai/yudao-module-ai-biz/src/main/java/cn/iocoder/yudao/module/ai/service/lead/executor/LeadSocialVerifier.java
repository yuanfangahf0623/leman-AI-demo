package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawlResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SearchEvidence;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SocialProfile;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SocialVerificationResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies public business social and external-web presence.
 */
public class LeadSocialVerifier {

    private static final Map<String, List<String>> SOCIAL_PLATFORMS = Map.of(
            "facebook", List.of("facebook.com", "fb.com"),
            "instagram", List.of("instagram.com"),
            "tiktok", List.of("tiktok.com"),
            "xiaohongshu", List.of("xiaohongshu.com", "xhslink.com", "xhs.cn"),
            "linkedin", List.of("linkedin.com"),
            "youtube", List.of("youtube.com", "youtu.be"),
            "pinterest", List.of("pinterest.com"));
    private static final List<String> NON_PROFILE_PATH_MARKERS = List.of(
            "/share", "/sharer", "/intent", "/login", "/privacy", "/policy", "/plugins", "/dialog", "/oauth");
    private static final List<String> ACTIVITY_WORDS = List.of(
            "post", "posts", "video", "videos", "reel", "reels", "update", "updates", "followers",
            "likes", "views", "动态", "笔记", "视频", "粉丝");
    private static final String SOCIAL_COMPLIANCE_NOTE = "Social verification uses only public business profile links "
            + "and public search snippets; no private social accounts, login-gated data, captcha bypassing, "
            + "or automated messaging.";

    private final LeadSearchProvider provider;

    public LeadSocialVerifier(LeadSearchProvider provider) {
        this.provider = provider;
    }

    public SocialVerificationResult verify(LeadRecord lead, CrawlResult crawlResult) {
        Map<String, SocialProfile> profilesByUrl = new LinkedHashMap<>();
        Map<String, String> externalUrlsByDomain = new LinkedHashMap<>();

        for (SocialProfile profile : profilesLinkedFromWebsite(lead, crawlResult)) {
            profilesByUrl.put(profile.getUrl(), profile);
            rememberExternalUrl(profile.getUrl(), lead, externalUrlsByDomain);
        }

        for (SearchEvidence item : searchPublicEvidence(lead)) {
            String url = LeadAgentUtils.normalizeUrl(item.getUrl());
            if (!LeadAgentUtils.hasText(url)) {
                continue;
            }
            String evidenceText = LeadAgentUtils.compactWhitespace(item.getTitle() + " " + item.getSnippet() + " " + url);
            boolean matchedCompany = matchesCompany(evidenceText, lead);
            boolean matchedDomain = matchesDomain(evidenceText, lead);
            String platform = detectSocialPlatform(url);
            if (platform != null && (matchedCompany || matchedDomain)) {
                SocialProfile searchProfile = SocialProfile.builder()
                        .platform(platform)
                        .url(url)
                        .source("search")
                        .matchedCompany(matchedCompany)
                        .matchedDomain(matchedDomain)
                        .activityStatus(activityStatus(evidenceText))
                        .evidence(LeadAgentUtils.truncate(evidenceText, 300))
                        .build();
                profilesByUrl.merge(url, searchProfile, this::mergeProfiles);
            }
            if (matchedCompany || matchedDomain) {
                rememberExternalUrl(url, lead, externalUrlsByDomain);
            }
        }

        List<SocialProfile> profiles = new ArrayList<>(profilesByUrl.values());
        List<String> externalUrls = new ArrayList<>(externalUrlsByDomain.values());
        Score score = scoreSocialVerification(profiles, externalUrls.size());
        return SocialVerificationResult.builder()
                .profiles(profiles)
                .externalAppearanceCount(externalUrls.size())
                .externalAppearanceUrls(externalUrls)
                .confidenceScore(score.score())
                .confidenceReason(score.reason())
                .activitySummary(score.activitySummary())
                .build();
    }

    public LeadRecord applySocialVerification(LeadRecord lead, SocialVerificationResult result) {
        lead.setSocialProfiles(result.getProfiles());
        lead.setSocialActivitySummary(result.getActivitySummary());
        lead.setSocialVerificationScore(result.getConfidenceScore());
        lead.setSocialVerificationReason(result.getConfidenceReason());
        lead.setExternalAppearanceCount(result.getExternalAppearanceCount());
        lead.setExternalAppearanceUrls(result.getExternalAppearanceUrls());
        if (lead.getComplianceNote() == null || !lead.getComplianceNote().contains(SOCIAL_COMPLIANCE_NOTE)) {
            lead.setComplianceNote((lead.getComplianceNote() == null ? "" : lead.getComplianceNote() + " ")
                    + SOCIAL_COMPLIANCE_NOTE);
        }
        return lead;
    }

    private List<SocialProfile> profilesLinkedFromWebsite(LeadRecord lead, CrawlResult crawlResult) {
        List<SocialProfile> profiles = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (var page : crawlResult.getPages()) {
            Document document = Jsoup.parse(page.getHtml() == null ? "" : page.getHtml(), page.getUrl());
            for (Element link : document.select("a[href]")) {
                String url = LeadAgentUtils.normalizeUrl(link.attr("href"), page.getUrl());
                if (!LeadAgentUtils.hasText(url) || seen.containsKey(url) || looksLikeShareOrLoginUrl(url)) {
                    continue;
                }
                String platform = detectSocialPlatform(url);
                if (platform == null) {
                    continue;
                }
                seen.put(url, true);
                String pageText = LeadAgentUtils.compactWhitespace(page.getTitle() + " " + page.getText() + " " + url);
                profiles.add(SocialProfile.builder()
                        .platform(platform)
                        .url(url)
                        .source("website")
                        .sourceUrl(page.getUrl())
                        .matchedCompany(matchesCompany(pageText, lead))
                        .matchedDomain(true)
                        .activityStatus("profile_found")
                        .evidence("Linked from public " + page.getPageType() + " page")
                        .build());
            }
        }
        return profiles;
    }

    private List<SearchEvidence> searchPublicEvidence(LeadRecord lead) {
        String query = buildSocialQuery(lead);
        if (!LeadAgentUtils.hasText(query)) {
            return List.of();
        }
        return provider.searchEvidence(query, lead.getCountry(), 12);
    }

    private void rememberExternalUrl(String url, LeadRecord lead, Map<String, String> externalUrlsByDomain) {
        String domain = LeadAgentUtils.extractDomain(url);
        if (!LeadAgentUtils.hasText(domain) || domain.equals(lead.getDomain())) {
            return;
        }
        externalUrlsByDomain.putIfAbsent(domain, url);
    }

    private SocialProfile mergeProfiles(SocialProfile existing, SocialProfile incoming) {
        String activity = activityRank(existing.getActivityStatus()) >= activityRank(incoming.getActivityStatus())
                ? existing.getActivityStatus() : incoming.getActivityStatus();
        existing.setSource(existing.getSource() + "+" + incoming.getSource());
        existing.setMatchedCompany(existing.isMatchedCompany() || incoming.isMatchedCompany());
        existing.setMatchedDomain(existing.isMatchedDomain() || incoming.isMatchedDomain());
        existing.setActivityStatus(activity);
        existing.setEvidence(LeadAgentUtils.truncate((existing.getEvidence() == null ? "" : existing.getEvidence())
                + " | " + (incoming.getEvidence() == null ? "" : incoming.getEvidence()), 500));
        return existing;
    }

    private String detectSocialPlatform(String url) {
        String host = "";
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        for (Map.Entry<String, List<String>> entry : SOCIAL_PLATFORMS.entrySet()) {
            String finalHost = host;
            if (entry.getValue().stream().anyMatch(domain -> finalHost.equals(domain) || finalHost.endsWith("." + domain))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean looksLikeShareOrLoginUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return NON_PROFILE_PATH_MARKERS.stream().anyMatch(lower::contains);
    }

    private String buildSocialQuery(LeadRecord lead) {
        String name = LeadAgentUtils.compactWhitespace(lead.getCompanyName());
        String domain = LeadAgentUtils.hasText(lead.getDomain()) ? lead.getDomain() : LeadAgentUtils.extractDomain(lead.getWebsite());
        String suffix = " facebook tiktok instagram linkedin youtube xiaohongshu 小红书";
        if (LeadAgentUtils.hasText(name) && LeadAgentUtils.hasText(domain)) {
            return "\"" + name + "\" \"" + domain + "\"" + suffix;
        }
        if (LeadAgentUtils.hasText(name)) {
            return "\"" + name + "\"" + suffix;
        }
        if (LeadAgentUtils.hasText(domain)) {
            return "\"" + domain + "\"" + suffix;
        }
        return "";
    }

    private boolean matchesCompany(String text, LeadRecord lead) {
        String company = LeadAgentUtils.compactWhitespace(lead.getCompanyName()).toLowerCase(Locale.ROOT);
        if (!LeadAgentUtils.hasText(company)) {
            return false;
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains(company)) {
            return true;
        }
        long hits = List.of(company.split("\\s+")).stream()
                .filter(token -> token.length() >= 4)
                .filter(lower::contains)
                .count();
        return hits >= Math.max(1, company.split("\\s+").length / 2);
    }

    private boolean matchesDomain(String text, LeadRecord lead) {
        String domain = LeadAgentUtils.hasText(lead.getDomain()) ? lead.getDomain() : LeadAgentUtils.extractDomain(lead.getWebsite());
        if (!LeadAgentUtils.hasText(domain)) {
            return false;
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String stem = domain.split("\\.", 2)[0];
        return lower.contains(domain.toLowerCase(Locale.ROOT))
                || lower.replace("-", "").contains(stem.replace("-", "").toLowerCase(Locale.ROOT));
    }

    private String activityStatus(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int year = LocalDate.now().getYear();
        boolean hasActivityWord = ACTIVITY_WORDS.stream().anyMatch(lower::contains);
        boolean hasRecentYear = lower.contains(String.valueOf(year)) || lower.contains(String.valueOf(year - 1));
        if (hasActivityWord && hasRecentYear) {
            return "active";
        }
        if (hasActivityWord) {
            return "public_activity_visible";
        }
        return "profile_found";
    }

    private Score scoreSocialVerification(List<SocialProfile> profiles, int externalAppearanceCount) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        long verified = profiles.stream()
                .filter(profile -> "website".equals(profile.getSource()) || profile.isMatchedCompany() || profile.isMatchedDomain())
                .count();
        long active = profiles.stream().filter(profile -> "active".equals(profile.getActivityStatus())).count();
        if (verified > 0) {
            score += 8;
            reasons.add(verified + " public social profile(s) matched");
        }
        if (active > 0) {
            score += 5;
            reasons.add(active + " profile(s) show recent public activity");
        }
        if (externalAppearanceCount >= 3) {
            score += 10;
            reasons.add(externalAppearanceCount + " external site appearances");
        } else if (externalAppearanceCount >= 1) {
            score += 5;
            reasons.add(externalAppearanceCount + " external site appearance(s)");
        }
        if (reasons.isEmpty()) {
            reasons.add("no public social or external verification evidence found");
        }
        String activitySummary = buildActivitySummary(profiles, externalAppearanceCount);
        return new Score(Math.min(20, score), String.join("; ", reasons), activitySummary);
    }

    private String buildActivitySummary(List<SocialProfile> profiles, int externalAppearanceCount) {
        if (profiles.isEmpty() && externalAppearanceCount == 0) {
            return "未找到公开社交媒体或外部出现佐证";
        }
        List<String> parts = new ArrayList<>();
        List<String> platforms = profiles.stream().map(SocialProfile::getPlatform).distinct().sorted().toList();
        List<String> activePlatforms = profiles.stream()
                .filter(profile -> "active".equals(profile.getActivityStatus()))
                .map(SocialProfile::getPlatform)
                .distinct()
                .sorted()
                .toList();
        if (!platforms.isEmpty()) {
            parts.add("公开社交平台: " + String.join(", ", platforms));
        }
        if (!activePlatforms.isEmpty()) {
            parts.add("近期动态线索: " + String.join(", ", activePlatforms));
        }
        if (externalAppearanceCount > 0) {
            parts.add("不同外部网站出现: " + externalAppearanceCount);
        }
        return String.join("; ", parts);
    }

    private int activityRank(String value) {
        if ("active".equals(value)) {
            return 3;
        }
        if ("public_activity_visible".equals(value)) {
            return 2;
        }
        if ("profile_found".equals(value)) {
            return 1;
        }
        return 0;
    }

    private record Score(int score, String reason, String activitySummary) {
    }

}
