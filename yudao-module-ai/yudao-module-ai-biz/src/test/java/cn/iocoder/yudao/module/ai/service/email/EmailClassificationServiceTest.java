package cn.iocoder.yudao.module.ai.service.email;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationDTO;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationRequest;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.ai.enums.AiEmailClassificationErrorCodeConstants.EMAIL_CLASSIFICATION_RESPONSE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailClassificationServiceTest {

    @Test
    void classifyShouldSupportNonRfqEmailTypes() {
        AtomicReference<AiChatModelRequest> capturedRequest = new AtomicReference<>();
        AiChatModelService chatModelService = request -> {
            capturedRequest.set(request);
            return response("""
                    {"is_match":true,"classification_type":"FINANCE_INVOICE","confidence":0.93,"reason":"invoice attached","risk_flags":[]}
                    """);
        };
        EmailClassificationService service = new EmailClassificationService(chatModelService, new ObjectMapper());

        EmailClassificationDTO result = service.classify(buildEmail(), EmailClassificationRequest.builder()
                .systemPrompt("You classify finance emails only.")
                .allowedTypes(List.of("FINANCE_INVOICE", "NON_FINANCE"))
                .positiveTypes(List.of("FINANCE_INVOICE"))
                .nonMatchType("NON_FINANCE")
                .callType("FINANCE_EMAIL_CLASSIFICATION")
                .build());

        assertTrue(result.getMatch());
        assertEquals("FINANCE_INVOICE", result.getClassificationType());
        assertEquals(new BigDecimal("0.93"), result.getConfidence());
        assertEquals("FINANCE_EMAIL_CLASSIFICATION", capturedRequest.get().getMetadata().get("callType"));
    }

    @Test
    void classifyShouldRejectTypeConflictWithIsMatch() {
        AiChatModelService chatModelService = request -> response("""
                {"is_match":true,"classification_type":"NON_FINANCE","confidence":0.88,"reason":"bad","risk_flags":[]}
                """);
        EmailClassificationService service = new EmailClassificationService(chatModelService, new ObjectMapper());

        ServiceException exception = assertThrows(ServiceException.class, () -> service.classify(buildEmail(),
                EmailClassificationRequest.builder()
                        .systemPrompt("You classify finance emails only.")
                        .allowedTypes(List.of("FINANCE_INVOICE", "NON_FINANCE"))
                        .positiveTypes(List.of("FINANCE_INVOICE"))
                        .nonMatchType("NON_FINANCE")
                        .build()));

        assertEquals(EMAIL_CLASSIFICATION_RESPONSE_INVALID, exception.getCode());
    }

    private EmailDTO buildEmail() {
        return EmailDTO.builder()
                .from("supplier@example.com")
                .subject("Invoice 2026-06")
                .bodyText("Please see attached invoice.")
                .build();
    }

    private AiChatModelResponse response(String content) {
        return AiChatModelResponse.builder()
                .model("unit-test")
                .content(content)
                .promptTokens(10)
                .completionTokens(20)
                .build();
    }

}
