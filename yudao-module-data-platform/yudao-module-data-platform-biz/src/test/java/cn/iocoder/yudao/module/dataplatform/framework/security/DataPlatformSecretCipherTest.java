package cn.iocoder.yudao.module.dataplatform.framework.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.dataplatform.framework.config.DataPlatformProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataPlatformSecretCipherTest {

    @Test
    void shouldEncryptAndDecryptWithoutPlaintextLeak() {
        DataPlatformSecretCipher cipher = cipher("0123456789abcdef0123456789abcdef");
        String encrypted = cipher.encrypt("warehouse-password");

        assertNotEquals("warehouse-password", encrypted);
        assertEquals("warehouse-password", cipher.decrypt(encrypted));
    }

    @Test
    void shouldRejectInvalidDeploymentKey() {
        DataPlatformProperties properties = new DataPlatformProperties();
        properties.setSecretKey("not-base64");
        DataPlatformSecretCipher cipher = new DataPlatformSecretCipher(properties);

        assertThrows(ServiceException.class, () -> cipher.encrypt("secret"));
    }

    private DataPlatformSecretCipher cipher(String key) {
        DataPlatformProperties properties = new DataPlatformProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)));
        return new DataPlatformSecretCipher(properties);
    }
}
