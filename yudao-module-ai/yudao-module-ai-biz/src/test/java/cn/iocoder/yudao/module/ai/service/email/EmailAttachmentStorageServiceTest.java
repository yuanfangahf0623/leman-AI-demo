package cn.iocoder.yudao.module.ai.service.email;

import cn.iocoder.yudao.module.ai.dal.dataobject.EmailAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.mysql.EmailAttachmentMapper;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class EmailAttachmentStorageServiceTest {

    @Test
    void storeEmailAttachmentsShouldStoreRawFileAndPersistExtractedText() throws Exception {
        EmailAttachmentMapper mapper = mock(EmailAttachmentMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        EmailAttachmentStorageService service = new EmailAttachmentStorageService(mapper, fileStorageService);
        byte[] content = "raw-pdf-content".getBytes(StandardCharsets.UTF_8);
        when(mapper.selectByMessageHashAndContentHash(eq(7L), anyString(), anyString())).thenReturn(null);
        when(fileStorageService.store(anyString(), eq(content)))
                .thenReturn(new FileStorageResult("email/attachment/7/test.pdf", "https://minio/bucket/test.pdf"));

        service.storeEmailAttachments(buildRawEmail(), EmailDTO.builder()
                .attachments(List.of(EmailDTO.Attachment.builder()
                        .fileName("quote.pdf")
                        .contentType("application/pdf")
                        .size((long) content.length)
                        .text("extracted text")
                        .content(content)
                        .build()))
                .build());

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).store(objectKeyCaptor.capture(), contentCaptor.capture());
        assertArrayEquals(content, contentCaptor.getValue());
        assertNotNull(objectKeyCaptor.getValue());

        ArgumentCaptor<EmailAttachmentDO> recordCaptor = ArgumentCaptor.forClass(EmailAttachmentDO.class);
        verify(mapper).insert(recordCaptor.capture());
        EmailAttachmentDO record = recordCaptor.getValue();
        assertEquals(7L, record.getTenantId());
        assertEquals("yuanf@leman-tech.com", record.getAccount());
        assertEquals("<message-1@example.com>", record.getMessageId());
        assertEquals("quote.pdf", record.getFileName());
        assertEquals("application/pdf", record.getContentType());
        assertEquals("email/attachment/7/test.pdf", record.getObjectKey());
        assertEquals("https://minio/bucket/test.pdf", record.getSourceUri());
        assertEquals(sha256Hex(content), record.getContentHash());
        assertEquals("extracted text", record.getExtractedText());
    }

    @Test
    void storeEmailAttachmentsShouldSkipExistingAttachment() {
        EmailAttachmentMapper mapper = mock(EmailAttachmentMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        EmailAttachmentStorageService service = new EmailAttachmentStorageService(mapper, fileStorageService);
        byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
        when(mapper.selectByMessageHashAndContentHash(eq(7L), anyString(), anyString()))
                .thenReturn(new EmailAttachmentDO());

        service.storeEmailAttachments(buildRawEmail(), EmailDTO.builder()
                .attachments(List.of(EmailDTO.Attachment.builder()
                        .fileName("quote.pdf")
                        .size((long) content.length)
                        .content(content)
                        .build()))
                .build());

        verify(fileStorageService, never()).store(anyString(), eq(content));
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(EmailAttachmentDO.class));
    }

    @Test
    void storeEmailAttachmentsShouldIgnoreDuplicateKeyOnInsert() {
        EmailAttachmentMapper mapper = mock(EmailAttachmentMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        EmailAttachmentStorageService service = new EmailAttachmentStorageService(mapper, fileStorageService);
        byte[] content = "duplicate-inline-image".getBytes(StandardCharsets.UTF_8);
        when(mapper.selectByMessageHashAndContentHash(eq(7L), anyString(), anyString())).thenReturn(null);
        when(fileStorageService.store(anyString(), eq(content)))
                .thenReturn(new FileStorageResult("email/attachment/7/image.png", "https://minio/bucket/image.png"));
        doThrow(new DuplicateKeyException("duplicate")).when(mapper)
                .insert(org.mockito.ArgumentMatchers.any(EmailAttachmentDO.class));

        assertDoesNotThrow(() -> service.storeEmailAttachments(buildRawEmail(), EmailDTO.builder()
                .attachments(List.of(EmailDTO.Attachment.builder()
                        .fileName("image.png")
                        .size((long) content.length)
                        .content(content)
                        .build()))
                .build()));
    }

    @Test
    void bindRfqIdShouldUpdateByMessageHash() {
        EmailAttachmentMapper mapper = mock(EmailAttachmentMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        EmailAttachmentStorageService service = new EmailAttachmentStorageService(mapper, fileStorageService);

        service.bindRfqId(buildRawEmail(), 99L);

        verify(mapper).updateRfqIdByMessageHash(eq(7L), anyString(), eq(99L));
    }

    private EmailRawDTO buildRawEmail() {
        return EmailRawDTO.builder()
                .tenantId(7L)
                .uid(123L)
                .account("yuanf@leman-tech.com")
                .messageId("<message-1@example.com>")
                .receivedTime(LocalDateTime.of(2026, 6, 25, 10, 0))
                .build();
    }

    private String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

}
