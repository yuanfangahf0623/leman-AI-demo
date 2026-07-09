package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_HERMES_RESPONSE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesRfqAgentServiceTest {

    @Test
    void extractRfqShouldReturnStructuredRfqFlag() {
        HermesRfqAgentService service = newService("""
                {"is_rfq":true,"confidence":0.92,"customer":"ACME","products":[{"name":"CNC part","quantity":"100","unit":"pcs","specifications":"Aluminum","raw_text":"CNC part x100"}],"risk_score":18,"missing_info":["drawing"],"next_actions":["ask drawing"]}
                """);

        HermesRfqDTO result = service.extractRfq(EmailDTO.builder()
                .from("buyer@example.com")
                .subject("RFQ CNC part")
                .bodyText("Please quote CNC part x100")
                .build());

        assertTrue(result.getIsRfq());
        assertEquals("ACME", result.getCustomer());
        assertEquals(new BigDecimal("0.92"), result.getConfidence());
        assertEquals("CNC part", result.getProducts().get(0).getName());
        assertEquals("drawing", result.getMissingInfo().get(0));
    }

    @Test
    void extractRfqShouldNormalizeStringProducts() {
        HermesRfqAgentService service = newService("""
                {"is_rfq":true,"confidence":0.91,"customer":"ACME","products":["CNC part x100"],"risk_score":16,"missing_info":[],"next_actions":["costing"]}
                """);

        HermesRfqDTO result = service.extractRfq(EmailDTO.builder()
                .from("buyer@example.com")
                .subject("RFQ CNC part")
                .bodyText("Please quote CNC part x100")
                .build());

        assertTrue(result.getIsRfq());
        assertEquals("CNC part x100", result.getProducts().get(0).getRawText());
    }

    @Test
    void extractRfqShouldReturnNonRfqFlag() {
        HermesRfqAgentService service = newService("""
                {"is_rfq":false,"confidence":0.87,"customer":null,"products":[],"risk_score":0,"missing_info":[],"next_actions":[]}
                """);

        HermesRfqDTO result = service.extractRfq(EmailDTO.builder()
                .from("newsletter@example.com")
                .subject("Newsletter")
                .bodyText("Monthly update")
                .build());

        assertFalse(result.getIsRfq());
    }

    @Test
    void extractRfqShouldRejectNonStrictJson() {
        HermesRfqAgentService service = newService("""
                ```json
                {"is_rfq":false,"confidence":0.8,"customer":null,"products":[],"risk_score":0,"missing_info":[],"next_actions":[]}
                ```
                """);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.extractRfq(EmailDTO.builder()
                .bodyText("hello")
                .build()));

        assertEquals(RFQ_HERMES_RESPONSE_INVALID, exception.getCode());
    }

    private HermesRfqAgentService newService(String responseContent) {
        AiChatModelService chatModelService = request -> AiChatModelResponse.builder()
                .model("unit-test")
                .content(responseContent)
                .promptTokens(10)
                .completionTokens(20)
                .build();
        return new HermesRfqAgentService(new AiRfqProperties(), chatModelService, new ObjectMapper());
    }

}
