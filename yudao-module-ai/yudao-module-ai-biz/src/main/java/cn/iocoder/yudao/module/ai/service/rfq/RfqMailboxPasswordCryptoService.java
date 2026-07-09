package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_CONFIG_INVALID;

/**
 * Encrypts reversible mailbox passwords. Masking is only for display.
 */
@Service
@RequiredArgsConstructor
public class RfqMailboxPasswordCryptoService {

    private static final String VERSION = "v1";
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final AiRfqProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox password is empty");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + base64(nonce) + ":" + base64(encrypted);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Encrypt mailbox password failed");
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox encrypted password is empty");
        }
        String[] parts = cipherText.split(":");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox encrypted password format is invalid");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Decrypt mailbox password failed");
        }
    }

    public String mask(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        String value = plainText.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private SecretKeySpec key() throws Exception {
        String passwordKey = properties.getSecurity() == null ? null : properties.getSecurity().getPasswordKey();
        if (!StringUtils.hasText(passwordKey)) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "RFQ mailbox password key is not configured");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, AES);
    }

    private String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

}
