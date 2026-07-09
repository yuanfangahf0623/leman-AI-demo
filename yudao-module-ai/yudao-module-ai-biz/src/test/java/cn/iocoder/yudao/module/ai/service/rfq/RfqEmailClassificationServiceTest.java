package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.email.EmailClassificationService;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.RfqEmailClassificationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.ai.enums.AiEmailClassificationErrorCodeConstants.EMAIL_CLASSIFICATION_RESPONSE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfqEmailClassificationServiceTest {

    @Test
    void classifyShouldReturnNewRfq() {
        RfqEmailClassificationService service = newService("""
                {"is_match":true,"classification_type":"NEW_RFQ","confidence":0.94,"reason":"RFQ subject","risk_flags":[]}
                """);

        RfqEmailClassificationDTO result = service.classify(buildEmail("RFQ CNC part"));

        assertTrue(result.getIsRfq());
        assertEquals(RfqEmailClassificationDTO.TYPE_NEW_RFQ, result.getRfqType());
        assertEquals(new BigDecimal("0.94"), result.getConfidence());
    }

    @Test
    void classifyShouldReturnInboundRfqThreadReply() {
        RfqEmailClassificationService service = newService("""
                {"is_match":true,"classification_type":"RFQ_THREAD_REPLY","confidence":0.88,"reason":"RFQ reply thread","risk_flags":["reply"]}
                """);

        RfqEmailClassificationDTO result = service.classify(buildEmail("RE: RFQ CNC part"));

        assertTrue(result.getIsRfq());
        assertEquals(RfqEmailClassificationDTO.TYPE_RFQ_THREAD_REPLY, result.getRfqType());
        assertEquals("reply", result.getRiskFlags().get(0));
    }

    @Test
    void classifyShouldReturnNonRfq() {
        RfqEmailClassificationService service = newService("""
                {"is_match":false,"classification_type":"NON_RFQ","confidence":0.91,"reason":"marketing follow-up","risk_flags":[]}
                """);

        RfqEmailClassificationDTO result = service.classify(buildEmail("Follow up from vendor"));

        assertFalse(result.getIsRfq());
        assertEquals(RfqEmailClassificationDTO.TYPE_NON_RFQ, result.getRfqType());
    }

    @Test
    void classifyShouldRejectNonStrictJson() {
        RfqEmailClassificationService service = newService("""
                ```json
                {"is_match":false,"classification_type":"NON_RFQ","confidence":0.91,"reason":"marketing follow-up","risk_flags":[]}
                ```
                """);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.classify(buildEmail("hello")));

        assertEquals(EMAIL_CLASSIFICATION_RESPONSE_INVALID, exception.getCode());
    }

    @Test
    void classifyShouldPassConfiguredModel() {
        AiRfqProperties properties = new AiRfqProperties();
        properties.getClassification().setModel("gpt-5.5");
        AtomicReference<AiChatModelRequest> capturedRequest = new AtomicReference<>();
        AiChatModelService chatModelService = request -> {
            capturedRequest.set(request);
            return response("""
                    {"is_match":false,"classification_type":"NON_RFQ","confidence":0.91,"reason":"marketing follow-up","risk_flags":[]}
                    """);
        };
        RfqEmailClassificationService service = new RfqEmailClassificationService(
                properties, new EmailClassificationService(chatModelService, new ObjectMapper()));

        service.classify(buildEmail("Follow up"));

        assertEquals("gpt-5.5", capturedRequest.get().getModel());
    }

    @Test
    void classifyShouldUseDedicatedClassifierPrompt() {
        AtomicReference<AiChatModelRequest> capturedRequest = new AtomicReference<>();
        AiChatModelService chatModelService = request -> {
            capturedRequest.set(request);
            return response("""
                    {"is_match":false,"classification_type":"NON_RFQ","confidence":0.91,"reason":"marketing follow-up","risk_flags":[]}
                    """);
        };
        RfqEmailClassificationService service = new RfqEmailClassificationService(
                new AiRfqProperties(), new EmailClassificationService(chatModelService, new ObjectMapper()));

        service.classify(buildEmail("Follow up"));

        String systemPrompt = capturedRequest.get().getSystemPrompt();
        assertTrue(systemPrompt.contains("RFQ classification specialist"));
        assertTrue(systemPrompt.contains("Your only job is to classify"));
    }

    private RfqEmailClassificationService newService(String responseContent) {
        AiChatModelService chatModelService = request -> response(responseContent);
        return new RfqEmailClassificationService(new AiRfqProperties(),
                new EmailClassificationService(chatModelService, new ObjectMapper()));
    }

    private AiChatModelResponse response(String content) {
        return AiChatModelResponse.builder()
                .model("unit-test")
                .content(content)
                .promptTokens(10)
                .completionTokens(20)
                .build();
    }

    private EmailDTO buildEmail(String subject) {
        return EmailDTO.builder()
                .from("buyer@example.com")
                .subject(subject)
                .bodyText("Please review the email.")
                .build();
    }

}
