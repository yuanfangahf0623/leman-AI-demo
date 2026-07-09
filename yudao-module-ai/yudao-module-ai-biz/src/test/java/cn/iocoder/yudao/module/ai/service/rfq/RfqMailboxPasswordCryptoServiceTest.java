package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RfqMailboxPasswordCryptoServiceTest {

    @Test
    void encryptShouldDecryptBackToPlainTextAndKeepMaskDisplayOnly() {
        RfqMailboxPasswordCryptoService service = newService("unit-test-password-key");

        String cipherText = service.encrypt("client-password-123");

        assertNotEquals("client-password-123", cipherText);
        assertEquals("client-password-123", service.decrypt(cipherText));
        assertEquals("cl****23", service.mask("client-password-123"));
        assertFalse(service.mask("client-password-123").contains("client-password-123"));
    }

    @Test
    void encryptShouldRejectMissingKey() {
        RfqMailboxPasswordCryptoService service = newService("");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.encrypt("password"));

        assertEquals(RFQ_EMAIL_CONFIG_INVALID, exception.getCode());
    }

    private RfqMailboxPasswordCryptoService newService(String passwordKey) {
        AiRfqProperties properties = new AiRfqProperties();
        properties.getSecurity().setPasswordKey(passwordKey);
        return new RfqMailboxPasswordCryptoService(properties);
    }

}
