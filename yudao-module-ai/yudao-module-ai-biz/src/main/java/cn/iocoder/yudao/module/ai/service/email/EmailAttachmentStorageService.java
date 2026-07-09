package cn.iocoder.yudao.module.ai.service.email;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.EmailAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.mysql.EmailAttachmentMapper;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static cn.iocoder.yudao.module.ai.enums.AiEmailClassificationErrorCodeConstants.EMAIL_ATTACHMENT_STORAGE_FAILED;

/**
 * Stores original email attachments and persists parsed attachment metadata/text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAttachmentStorageService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MAX_FILE_NAME_LENGTH = 512;
    private static final int MAX_CONTENT_TYPE_LENGTH = 255;

    private final EmailAttachmentMapper emailAttachmentMapper;
    private final FileStorageService fileStorageService;

    @Transactional(rollbackFor = Exception.class)
    public void storeEmailAttachments(EmailRawDTO rawEmail, EmailDTO emailDTO) {
        if (rawEmail == null || emailDTO == null || CollectionUtils.isEmpty(emailDTO.getAttachments())) {
            return;
        }
        Long tenantId = resolveTenantId(rawEmail);
        String messageId = resolveMessageId(rawEmail);
        for (EmailDTO.Attachment attachment : emailDTO.getAttachments()) {
            storeOne(rawEmail, tenantId, messageId, attachment);
        }
    }

    public void bindRfqId(String messageId, Long tenantId, Long rfqId) {
        if (!StringUtils.hasText(messageId) || rfqId == null) {
            return;
        }
        emailAttachmentMapper.updateRfqIdByMessageHash(firstTenantId(tenantId), sha256Hex(messageId.trim()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), rfqId);
    }

    public void bindRfqId(EmailRawDTO rawEmail, Long rfqId) {
        if (rawEmail == null || rfqId == null) {
            return;
        }
        bindRfqId(resolveMessageId(rawEmail), resolveTenantId(rawEmail), rfqId);
    }

    private void storeOne(EmailRawDTO rawEmail, Long tenantId, String messageId, EmailDTO.Attachment attachment) {
        if (attachment == null || attachment.getContent() == null || attachment.getContent().length == 0) {
            return;
        }
        String fileName = sanitizeFileName(attachment.getFileName());
        String contentHash = sha256Hex(attachment.getContent());
        String messageHash = sha256Hex(messageId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EmailAttachmentDO oldAttachment = emailAttachmentMapper.selectByMessageHashAndContentHash(
                tenantId, messageHash, contentHash);
        if (oldAttachment != null) {
            return;
        }
        try {
            String objectKey = buildObjectKey(tenantId, rawEmail, fileName, contentHash);
            FileStorageResult storageResult = fileStorageService.store(objectKey, attachment.getContent());
            EmailAttachmentDO record = new EmailAttachmentDO();
            record.setTenantId(tenantId);
            record.setAccount(limit(rawEmail.getAccount(), 255));
            record.setMessageId(limit(messageId, 512));
            record.setMessageHash(messageHash);
            record.setUid(rawEmail.getUid());
            record.setFileName(limit(fileName, MAX_FILE_NAME_LENGTH));
            record.setContentType(limit(attachment.getContentType(), MAX_CONTENT_TYPE_LENGTH));
            record.setFileSize(attachment.getSize() == null ? (long) attachment.getContent().length : attachment.getSize());
            record.setObjectKey(limit(storageResult.getObjectKey(), 1024));
            record.setSourceUri(limit(storageResult.getSourceUri(), 1024));
            record.setContentHash(contentHash);
            record.setExtractedText(attachment.getText());
            record.setDeleted(false);
            try {
                emailAttachmentMapper.insert(record);
            } catch (DuplicateKeyException ex) {
                log.info("Email attachment duplicate skipped, tenantId={}, account={}, uid={}, messageId={}, fileName={}",
                        tenantId, rawEmail.getAccount(), rawEmail.getUid(), messageId, safeFileName(fileName));
            }
        } catch (DuplicateKeyException ex) {
            log.info("Email attachment duplicate skipped, tenantId={}, account={}, uid={}, messageId={}, fileName={}",
                    tenantId, rawEmail.getAccount(), rawEmail.getUid(), messageId, safeFileName(fileName));
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Email attachment store failed, tenantId={}, account={}, uid={}, messageId={}, fileName={}, errorType={}",
                    tenantId, rawEmail.getAccount(), rawEmail.getUid(), messageId, safeFileName(fileName),
                    ex.getClass().getSimpleName());
            throw new ServiceException(EMAIL_ATTACHMENT_STORAGE_FAILED, "Email attachment store failed");
        }
    }

    private String buildObjectKey(Long tenantId, EmailRawDTO rawEmail, String fileName, String contentHash) {
        LocalDateTime receivedTime = rawEmail.getReceivedTime() == null ? LocalDateTime.now() : rawEmail.getReceivedTime();
        String month = MONTH_FORMATTER.format(receivedTime);
        String messageKey = sha256Hex(resolveMessageId(rawEmail).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String extension = extensionOf(fileName);
        String suffix = extension.isBlank() ? "" : "." + extension;
        return "email/attachment/" + tenantId + "/" + month + "/" + messageKey + "/"
                + contentHash.substring(0, 16) + "-" + UUID.randomUUID() + suffix;
    }

    private String resolveMessageId(EmailRawDTO rawEmail) {
        if (StringUtils.hasText(rawEmail.getMessageId())) {
            return rawEmail.getMessageId().trim();
        }
        return firstText(rawEmail.getAccount(), "unknown") + ":" + firstText(String.valueOf(rawEmail.getUid()), "0");
    }

    private Long resolveTenantId(EmailRawDTO rawEmail) {
        return firstTenantId(rawEmail.getTenantId());
    }

    private Long firstTenantId(Long tenantId) {
        return tenantId == null ? AiTenantContextHolder.getTenantId() : tenantId;
    }

    private String sanitizeFileName(String value) {
        String fileName = value == null ? "" : value.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (!StringUtils.hasText(fileName)) {
            return "attachment";
        }
        fileName = fileName.replaceAll("[/:*?\"<>|\\p{Cntrl}]+", "_");
        fileName = fileName.replace("..", "_");
        return limit(fileName, MAX_FILE_NAME_LENGTH);
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return extension.length() > 16 || !extension.matches("[a-z0-9]+") ? "" : extension;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(EMAIL_ATTACHMENT_STORAGE_FAILED, "Calculate email attachment hash failed");
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeFileName(String fileName) {
        return limit(fileName, 128);
    }

}
