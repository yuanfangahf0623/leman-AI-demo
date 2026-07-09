package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CandidateRecord;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ClassificationResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawlResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional LLM review that improves the deterministic lead judgement.
 */
@Component
@Slf4j
public class LeadLlmReviewer {

    private static final String PUBLIC_CONTEXT_NOTE = "Only public business webpage text and extracted public business "
            + "contact fields were sent to the AI model for lead qualification; no automated email sending.";

    private final AiChatModelService aiChatModelService;
    private final ObjectMapper objectMapper;

    public LeadLlmReviewer(AiChatModelService aiChatModelService, ObjectMapper objectMapper) {
        this.aiChatModelService = aiChatModelService;
        this.objectMapper = objectMapper;
    }

    public ReviewOutcome review(LeadRecord lead, CrawlResult crawlResult, ClassificationResult classification,
                                CandidateRecord searchRecord) {
        try {
            AiChatModelResponse response = aiChatModelService.chat(AiChatModelRequest.builder()
                    .systemPrompt(systemPrompt())
                    .userPrompt(buildReviewPrompt(lead, crawlResult, classification, searchRecord))
                    .temperature(0.2D)
                    .maxTokens(1200)
                    .metadata(Map.of("responseFormat", "json_object"))
                    .build());
            AiLeadReview review = objectMapper.readValue(response.getContent(), AiLeadReview.class);
            applyReview(lead, classification, review, response.getModel());
            return new ReviewOutcome(true, null, response.getModel());
        } catch (Exception ex) {
            log.warn("Lead AI review failed, domain={}, reason={}", lead.getDomain(), ex.getClass().getSimpleName());
            return new ReviewOutcome(false, LeadAgentUtils.truncate(ex.getMessage(), 1000), null);
        }
    }

    private String systemPrompt() {
        return "You are a careful B2B sales lead qualification analyst. Use only supplied public business "
                + "webpage text and extracted public business fields. Do not infer private personal information. "
                + "Do not invent emails, phones, or facts. Choose best_email only from the provided emails. "
                + "Return strict JSON only.";
    }

    private String buildReviewPrompt(LeadRecord lead, CrawlResult crawlResult, ClassificationResult classification,
                                     CandidateRecord searchRecord) throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("task", "Judge customer fit, choose/classify best email, score lead, and draft English B2B outreach.");
        context.put("target_customer_profiles", List.of(
                "European yacht sales companies, yacht dealers, yacht brokers, yacht manufacturers and yacht clubs",
                "European RV, motorhome, camper van and caravan sales companies or dealers",
                "European home appliance dealers, distributors, wholesalers, importers, agents and appliance retail chains",
                "European hotels, serviced apartments, real estate, interior, renovation, modular housing and tiny-house customers"));
        context.put("non_targets", List.of("news articles", "blogs", "forums", "job boards", "yellow-page aggregators",
                "large marketplaces", "sites with no clear product relevance"));
        context.put("scoring_rules", List.of(
                "High product relevance +30",
                "Clear B2B/wholesale/distributor signal +20",
                "Purchasing/owner/decision-maker email +20",
                "Generic contact email +10",
                "Phone +5",
                "Address or imprint +10",
                "Target European country +5",
                "Public social/external verification up to +20",
                "Large platform or aggregator -50",
                "No email -30",
                "Product completely unrelated -60"));
        context.put("email_type_rules", Map.of(
                LeadExtractor.EMAIL_PURCHASING, "purchasing, procurement, buying, purchase, buyer",
                LeadExtractor.EMAIL_DECISION_MAKER, "owner, ceo, founder, director, manager, or likely person-name mailbox",
                LeadExtractor.EMAIL_SALES, "sales, wholesale, b2b, export, dealer, distribution",
                LeadExtractor.EMAIL_GENERIC, "info, contact, service, office, hello, support"));
        context.put("search", searchRecord);
        context.put("rule_based_initial_result", Map.of(
                "is_target", classification.isTarget(),
                "matched_category", nullToEmpty(classification.getMatchedCategory()),
                "target_customer_type", nullToEmpty(classification.getTargetCustomerType()),
                "reason", nullToEmpty(classification.getReason()),
                "score", lead.getScore(),
                "score_reason", nullToEmpty(lead.getScoreReason())));
        context.put("extracted_lead", lead);
        context.put("public_pages", crawlResult.getPages().stream().limit(5).map(page -> Map.of(
                "url", nullToEmpty(page.getUrl()),
                "page_type", nullToEmpty(page.getPageType()),
                "title", nullToEmpty(page.getTitle()),
                "text_excerpt", LeadAgentUtils.truncate(LeadAgentUtils.compactWhitespace(page.getText()), 1400)
        )).toList());
        context.put("output_schema", Map.ofEntries(
                Map.entry("is_target", "boolean"),
                Map.entry("target_customer_type", "string or null"),
                Map.entry("matched_category", "configured category code or null"),
                Map.entry("classification_reason", "short reason"),
                Map.entry("best_email", "one email from extracted_lead.emails or null"),
                Map.entry("email_type", "purchase, decision maker, sales/wholesale, generic, or unknown"),
                Map.entry("main_products", "string array"),
                Map.entry("score", "0-100 integer"),
                Map.entry("score_reason", "concise scoring reason"),
                Map.entry("development_email_subject", "short English B2B email subject"),
                Map.entry("development_email_body", "short English B2B email draft")));
        return objectMapper.writeValueAsString(context);
    }

    private void applyReview(LeadRecord lead, ClassificationResult classification, AiLeadReview review, String model) {
        String validEmail = chooseValidReviewEmail(review.getBestEmail(), lead.getEmails());
        if (validEmail != null) {
            lead.setBestEmail(validEmail);
            lead.setEmailType(normalizeEmailType(review.getEmailType()));
        }
        if (review.getMainProducts() != null && !review.getMainProducts().isEmpty()) {
            lead.setMainProducts(LeadAgentUtils.uniquePreserveOrder(review.getMainProducts()));
        }
        lead.setMatchedCategory(LeadAgentUtils.hasText(review.getMatchedCategory()) ? review.getMatchedCategory()
                : lead.getMatchedCategory());
        lead.setTargetCustomerType(LeadAgentUtils.hasText(review.getTargetCustomerType())
                ? review.getTargetCustomerType() : lead.getTargetCustomerType());
        lead.setScore(LeadAgentUtils.clampScore(review.getScore()));
        lead.setScoreReason("OpenAI " + model + ": " + nullToEmpty(review.getScoreReason()));
        lead.setDevelopmentEmailSubject(review.getDevelopmentEmailSubject());
        lead.setDevelopmentEmailBody(review.getDevelopmentEmailBody());
        lead.setAnalysisProvider("openai:" + model);
        if (lead.getComplianceNote() == null || !lead.getComplianceNote().contains(PUBLIC_CONTEXT_NOTE)) {
            lead.setComplianceNote((lead.getComplianceNote() == null ? "" : lead.getComplianceNote() + " ")
                    + PUBLIC_CONTEXT_NOTE);
        }
        classification.setTarget(review.isTarget());
        classification.setTargetCustomerType(review.getTargetCustomerType());
        classification.setMatchedCategory(review.getMatchedCategory());
        classification.setReason("OpenAI " + model + ": " + nullToEmpty(review.getClassificationReason()));
        classification.setProductUnrelated(!LeadAgentUtils.hasText(review.getMatchedCategory()));
        lead.setTarget(review.isTarget());
    }

    private String chooseValidReviewEmail(String bestEmail, List<String> emails) {
        if (!LeadAgentUtils.hasText(bestEmail) || emails == null) {
            return null;
        }
        return emails.stream().filter(email -> email.equalsIgnoreCase(bestEmail)).findFirst().orElse(null);
    }

    private String normalizeEmailType(String emailType) {
        if (!LeadAgentUtils.hasText(emailType)) {
            return "unknown";
        }
        String value = emailType.trim();
        if (List.of(LeadExtractor.EMAIL_PURCHASING, LeadExtractor.EMAIL_DECISION_MAKER,
                LeadExtractor.EMAIL_SALES, LeadExtractor.EMAIL_GENERIC).contains(value)) {
            return value;
        }
        return "unknown";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ReviewOutcome(boolean applied, String error, String model) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiLeadReview {
        @JsonProperty("is_target")
        private boolean target;
        @JsonProperty("target_customer_type")
        private String targetCustomerType;
        @JsonProperty("matched_category")
        private String matchedCategory;
        @JsonProperty("classification_reason")
        private String classificationReason;
        @JsonProperty("best_email")
        private String bestEmail;
        @JsonProperty("email_type")
        private String emailType;
        @JsonProperty("main_products")
        private List<String> mainProducts;
        private int score;
        @JsonProperty("score_reason")
        private String scoreReason;
        @JsonProperty("development_email_subject")
        private String developmentEmailSubject;
        @JsonProperty("development_email_body")
        private String developmentEmailBody;
    }

}
