package cn.iocoder.yudao.module.dataplatform.framework.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.dataplatform.framework.config.DataPlatformProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.SECRET_KEY_INVALID;

@Component
@RequiredArgsConstructor
public class DataPlatformSecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DataPlatformProperties properties;

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(SECRET_KEY_INVALID, "数据源密码加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("invalid cipher payload");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(SECRET_KEY_INVALID, "数据源密码解密失败，请检查部署密钥");
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getSecretKey());
            if (bytes.length != 32) {
                throw new IllegalArgumentException("AES-256 requires 32 bytes");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception ex) {
            throw new ServiceException(SECRET_KEY_INVALID,
                    "DATA_PLATFORM_SECRET_KEY 必须是 32 字节密钥的 Base64 编码");
        }
    }
}
