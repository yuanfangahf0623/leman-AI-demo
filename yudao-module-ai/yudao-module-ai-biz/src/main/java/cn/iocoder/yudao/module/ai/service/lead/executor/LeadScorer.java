package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ClassificationResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Lead scoring rules.
 */
public class LeadScorer {

    public ScoreResult scoreLead(LeadRecord lead, ClassificationResult classification) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (classification.isProductRelevanceHigh()) {
            score += 30;
            reasons.add("+30 product relevance high");
        } else if (classification.getMatchedCategory() != null) {
            score += 20;
            reasons.add("+20 product relevance found");
        }
        if (classification.isHasB2bSignal()) {
            score += 20;
            reasons.add("+20 B2B/wholesale/distributor signal");
        }
        if (LeadAgentUtils.hasText(lead.getBestEmail())) {
            if (LeadExtractor.EMAIL_PURCHASING.equals(lead.getEmailType())
                    || LeadExtractor.EMAIL_DECISION_MAKER.equals(lead.getEmailType())) {
                score += 20;
                reasons.add("+20 " + lead.getEmailType());
            } else if (LeadExtractor.EMAIL_SALES.equals(lead.getEmailType())) {
                score += 15;
                reasons.add("+15 sales/wholesale email");
            } else {
                score += 10;
                reasons.add("+10 generic contact email");
            }
        } else {
            score -= 30;
            reasons.add("-30 no email");
        }
        if (LeadAgentUtils.hasText(lead.getPhone())) {
            score += 5;
            reasons.add("+5 phone found");
        }
        if (classification.isHasAddressOrImprint() || LeadAgentUtils.hasText(lead.getAboutPage())) {
            score += 10;
            reasons.add("+10 address/imprint/about signal");
        }
        if (LeadAgentUtils.TARGET_COUNTRIES.contains(lead.getCountry())) {
            score += 5;
            reasons.add("+5 target European country");
        }
        if (lead.getSocialVerificationScore() > 0) {
            int socialPoints = Math.min(20, Math.max(0, lead.getSocialVerificationScore()));
            score += socialPoints;
            reasons.add("+" + socialPoints + " public social/external verification");
        }
        if (classification.isLargePlatformOrAggregator()) {
            score -= 50;
            reasons.add("-50 large platform or aggregator");
        }
        if (classification.isProductUnrelated()) {
            score -= 60;
            reasons.add("-60 product unrelated");
        }
        int normalized = LeadAgentUtils.clampScore(score);
        reasons.add("grade " + LeadAgentUtils.gradeFromScore(normalized));
        return new ScoreResult(normalized, String.join("; ", reasons));
    }

    public record ScoreResult(int score, String reason) {
    }

}
