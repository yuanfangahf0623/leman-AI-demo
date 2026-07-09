package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.email.EmailClassificationService;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationDTO;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationRequest;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.RfqEmailClassificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RFQ-specific adapter around the common email classification service.
 */
@Service
@RequiredArgsConstructor
public class RfqEmailClassificationService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are the company's RFQ classification specialist.
            Your only job is to classify normalized inbound emails for RFQ automation.
            Do not extract RFQ details, create RFQ records, generate tasks, draft replies, or answer the email.
            Classify the normalized email content as exactly one of:
            NEW_RFQ: a new request for quotation, request for pricing, design-change quotation study, or quote request.
            RFQ_THREAD_REPLY: an inbound reply, forward, clarification, quote/offer follow-up, or continuation of an RFQ thread received in the mailbox.
            NON_RFQ: newsletters, product introductions, sales demos, internal discussion, meeting follow-up, marketing, spam, or unrelated service communication.
            Do not create RFQs only because the email mentions a product, service, trial, meeting, or price in a generic sales context.
            """;

    private final AiRfqProperties properties;
    private final EmailClassificationService emailClassificationService;

    public RfqEmailClassificationDTO classify(EmailDTO emailDTO) {
        EmailClassificationDTO result = emailClassificationService.classify(emailDTO, buildRequest());
        return RfqEmailClassificationDTO.builder()
                .isRfq(result.getMatch())
                .rfqType(result.getClassificationType())
                .confidence(result.getConfidence())
                .reason(result.getReason())
                .riskFlags(result.getRiskFlags())
                .rawJson(result.getRawJson())
                .build();
    }

    private EmailClassificationRequest buildRequest() {
        AiRfqProperties.ClassificationProperties classification = properties.getClassification();
        return EmailClassificationRequest.builder()
                .model(classification == null ? null : classification.getModel())
                .systemPrompt(resolveSystemPrompt(classification))
                .allowedTypes(List.of(
                        RfqEmailClassificationDTO.TYPE_NEW_RFQ,
                        RfqEmailClassificationDTO.TYPE_RFQ_THREAD_REPLY,
                        RfqEmailClassificationDTO.TYPE_NON_RFQ))
                .positiveTypes(List.of(
                        RfqEmailClassificationDTO.TYPE_NEW_RFQ,
                        RfqEmailClassificationDTO.TYPE_RFQ_THREAD_REPLY))
                .nonMatchType(RfqEmailClassificationDTO.TYPE_NON_RFQ)
                .callType("RFQ_EMAIL_CLASSIFICATION")
                .maxInputChars(classification == null ? null : classification.getMaxInputChars())
                .maxTokens(classification == null ? null : classification.getMaxTokens())
                .build();
    }

    private String resolveSystemPrompt(AiRfqProperties.ClassificationProperties classification) {
        String prompt = classification == null ? null : classification.getSystemPrompt();
        return StringUtils.hasText(prompt) ? prompt.trim() : DEFAULT_SYSTEM_PROMPT;
    }

}
