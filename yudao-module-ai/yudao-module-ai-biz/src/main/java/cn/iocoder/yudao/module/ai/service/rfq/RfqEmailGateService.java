package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic RFQ candidate gate before model extraction and before persistence.
 */
@Service
@RequiredArgsConstructor
public class RfqEmailGateService {

    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@([A-Z0-9.-]+\\.[A-Z]{2,})",
            Pattern.CASE_INSENSITIVE);

    private final AiRfqProperties properties;

    public GateResult evaluateBeforeHermes(EmailRawDTO rawEmail, EmailDTO emailDTO) {
        GateResult beforeClassification = evaluateBeforeClassification(rawEmail, emailDTO);
        if (!beforeClassification.isPassed()) {
            return beforeClassification;
        }
        if (!hasExplicitInquiryIntent(emailDTO)) {
            return GateResult.rejected("NO_EXPLICIT_RFQ_INTENT");
        }
        return GateResult.passed();
    }

    public GateResult evaluateBeforeClassification(EmailRawDTO rawEmail, EmailDTO emailDTO) {
        String sender = firstText(rawEmail == null ? null : rawEmail.getFrom(), emailDTO == null ? null : emailDTO.getFrom());
        if (isInternalSender(sender)) {
            return GateResult.rejected("INTERNAL_SENDER");
        }
        return GateResult.passed();
    }

    public GateResult evaluateBeforeCreate(HermesRfqDTO hermesRfq) {
        if (hermesRfq == null || !Boolean.TRUE.equals(hermesRfq.getIsRfq())) {
            return GateResult.rejected("HERMES_NOT_RFQ");
        }
        String customer = hermesRfq.getCustomer();
        if (containsKeyword(customer, detection().getInternalCompanyKeywords())
                || containsKeyword(customer, detection().getInternalSenderKeywords())) {
            return GateResult.rejected("INTERNAL_CUSTOMER");
        }
        return GateResult.passed();
    }

    private boolean isInternalSender(String sender) {
        if (!StringUtils.hasText(sender)) {
            return false;
        }
        if (containsKeyword(sender, detection().getInternalSenderKeywords())) {
            return true;
        }
        Matcher matcher = EMAIL_ADDRESS_PATTERN.matcher(sender);
        while (matcher.find()) {
            String domain = normalizeDomain(matcher.group(1));
            if (isInternalDomain(domain)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalDomain(String domain) {
        if (!StringUtils.hasText(domain) || CollectionUtils.isEmpty(detection().getInternalEmailDomains())) {
            return false;
        }
        for (String configuredDomain : detection().getInternalEmailDomains()) {
            String normalizedDomain = normalizeDomain(configuredDomain);
            if (StringUtils.hasText(normalizedDomain)
                    && (domain.equals(normalizedDomain) || domain.endsWith("." + normalizedDomain))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExplicitInquiryIntent(EmailDTO emailDTO) {
        if (emailDTO == null) {
            return false;
        }
        String searchableText = buildSearchableText(emailDTO);
        if (!StringUtils.hasText(searchableText) || CollectionUtils.isEmpty(detection().getIntentKeywords())) {
            return false;
        }
        return containsKeyword(searchableText, detection().getIntentKeywords());
    }

    private String buildSearchableText(EmailDTO emailDTO) {
        StringBuilder builder = new StringBuilder();
        append(builder, emailDTO.getSubject());
        append(builder, emailDTO.getBodyText());
        if (emailDTO.getAttachments() != null) {
            for (EmailDTO.Attachment attachment : emailDTO.getAttachments()) {
                if (attachment == null) {
                    continue;
                }
                append(builder, attachment.getFileName());
                append(builder, attachment.getText());
            }
        }
        return builder.toString();
    }

    private boolean containsKeyword(String text, java.util.List<String> keywords) {
        if (!StringUtils.hasText(text) || CollectionUtils.isEmpty(keywords)) {
            return false;
        }
        String normalizedText = normalize(text);
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private AiRfqProperties.DetectionProperties detection() {
        if (properties.getDetection() == null) {
            properties.setDetection(new AiRfqProperties.DetectionProperties());
        }
        return properties.getDetection();
    }

    private void append(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text).append('\n');
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDomain(String text) {
        String domain = normalize(text);
        while (domain.startsWith("@")) {
            domain = domain.substring(1);
        }
        return domain;
    }

    @Getter
    @AllArgsConstructor
    public static class GateResult {

        private final boolean passed;

        private final String reason;

        static GateResult passed() {
            return new GateResult(true, "PASSED");
        }

        static GateResult rejected(String reason) {
            return new GateResult(false, reason);
        }

    }

}
