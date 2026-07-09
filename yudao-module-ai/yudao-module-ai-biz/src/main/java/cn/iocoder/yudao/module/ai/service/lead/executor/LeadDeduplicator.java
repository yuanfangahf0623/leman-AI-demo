package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deduplicates by domain, best email, and similar company names.
 */
public class LeadDeduplicator {

    public List<LeadRecord> deduplicateLeads(List<LeadRecord> leads) {
        List<LeadRecord> kept = new ArrayList<>();
        Set<String> seenDomains = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        for (LeadRecord lead : leads.stream().sorted(Comparator.comparingInt(LeadRecord::getScore).reversed()).toList()) {
            String domain = normalize(lead.getDomain());
            String email = normalize(lead.getBestEmail());
            if (!domain.isBlank() && seenDomains.contains(domain)) {
                continue;
            }
            if (!email.isBlank() && seenEmails.contains(email)) {
                continue;
            }
            lead.setDuplicateStatus(companyDuplicateStatus(lead, kept));
            kept.add(lead);
            if (!domain.isBlank()) {
                seenDomains.add(domain);
            }
            if (!email.isBlank()) {
                seenEmails.add(email);
            }
        }
        return kept.stream().sorted(Comparator.comparingInt(LeadRecord::getScore).reversed()).toList();
    }

    private String companyDuplicateStatus(LeadRecord lead, List<LeadRecord> kept) {
        String current = normalizeCompanyName(lead.getCompanyName());
        if (current.isBlank()) {
            return "unique";
        }
        for (LeadRecord existing : kept) {
            String existingName = normalizeCompanyName(existing.getCompanyName());
            if (!existingName.isBlank() && similarity(current, existingName) >= 90) {
                return "possible_duplicate_company:" + existing.getDomain();
            }
        }
        return "unique";
    }

    private String normalizeCompanyName(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(gmbh|sarl|bv|ltd|limited|srl|sp z oo|store|shop)\\b", "")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int similarity(String left, String right) {
        if (left.equals(right)) {
            return 100;
        }
        int distance = levenshtein(left, right);
        int max = Math.max(left.length(), right.length());
        return max == 0 ? 100 : (int) Math.round((1.0 - (double) distance / max) * 100);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
