package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceAttachmentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceMapper;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_CONTENT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceInvoiceServiceImplTest {

    @Mock
    private FinanceInvoiceMapper invoiceMapper;
    @Mock
    private FinanceInvoiceAttachmentMapper attachmentMapper;
    @Mock
    private FinanceInvoiceAiService invoiceAiService;
    @Mock
    private FinanceInvoiceRiskService invoiceRiskService;
    @Mock
    private FinanceInvoiceApprovalService invoiceApprovalService;
    @Mock
    private FinanceSupplierPaymentInfoService supplierPaymentInfoService;
    @Mock
    private FileStorageService fileStorageService;

    private FinanceInvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        AiTenantContextHolder.setTenantId(1L);
        invoiceService = new FinanceInvoiceServiceImpl(invoiceMapper, attachmentMapper, invoiceAiService,
                invoiceRiskService, invoiceApprovalService, supplierPaymentInfoService, fileStorageService,
                new ObjectMapper(), new AiProperties());
    }

    @AfterEach
    void tearDown() {
        AiTenantContextHolder.clear();
    }

    @Test
    void createInvoiceFromSourceShouldAcceptDocxAndKeepEmailSourceFields() {
        byte[] content = new byte[]{'P', 'K', 3, 4, 20, 0};
        LocalDateTime receivedTime = LocalDateTime.of(2026, 6, 4, 10, 30);
        when(fileStorageService.store(anyString(), eq(content)))
                .thenReturn(new FileStorageResult("finance/invoice/1/email/test.docx",
                        "file:///finance/invoice/1/email/test.docx"));
        doAnswer(invocation -> {
            FinanceInvoiceDO invoice = invocation.getArgument(0);
            invoice.setId(100L);
            return 1;
        }).when(invoiceMapper).insert(any(FinanceInvoiceDO.class));
        when(invoiceMapper.updateByIdAndTenantId(any(FinanceInvoiceDO.class), eq(1L))).thenReturn(1);
        doAnswer(invocation -> {
            FinanceInvoiceAttachmentDO attachment = invocation.getArgument(0);
            attachment.setId(200L);
            return 1;
        }).when(attachmentMapper).insert(any(FinanceInvoiceAttachmentDO.class));

        Long id = invoiceService.createInvoiceFromSource(FinanceInvoiceSourceFile.builder()
                .sourceType(FinanceInvoiceConstants.SOURCE_EMAIL)
                .sourceMessageId("mail-20260604-001")
                .sourceSender("finance@example.com")
                .sourceReceivedTime(receivedTime)
                .fileName("invoice.docx")
                .fileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize((long) content.length)
                .content(content)
                .build());

        assertEquals(100L, id);
        ArgumentCaptor<FinanceInvoiceDO> invoiceCaptor = ArgumentCaptor.forClass(FinanceInvoiceDO.class);
        verify(invoiceMapper).insert(invoiceCaptor.capture());
        FinanceInvoiceDO invoice = invoiceCaptor.getValue();
        assertEquals(1L, invoice.getTenantId());
        assertEquals(FinanceInvoiceConstants.SOURCE_EMAIL, invoice.getSourceType());
        assertEquals("mail-20260604-001", invoice.getSourceMessageId());
        assertEquals("finance@example.com", invoice.getSourceSender());
        assertEquals(receivedTime, invoice.getSourceReceivedTime());
        assertEquals("invoice.docx", invoice.getFileName());
        assertEquals("docx", invoice.getFileType());
        assertEquals(FinanceInvoiceConstants.AI_STATUS_NOT_RECOGNIZED, invoice.getAiStatus());
        assertEquals(FinanceInvoiceConstants.APPROVAL_STATUS_DRAFT, invoice.getApprovalStatus());

        ArgumentCaptor<FinanceInvoiceAttachmentDO> attachmentCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceAttachmentDO.class);
        verify(attachmentMapper).insert(attachmentCaptor.capture());
        FinanceInvoiceAttachmentDO attachment = attachmentCaptor.getValue();
        assertEquals(100L, attachment.getInvoiceId());
        assertEquals("docx", attachment.getFileType());
    }

    @Test
    void createInvoiceFromSourceShouldRejectInvalidDocxContent() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceFromSource(FinanceInvoiceSourceFile.builder()
                        .sourceType(FinanceInvoiceConstants.SOURCE_EMAIL)
                        .fileName("invoice.docx")
                        .content(new byte[]{'N', 'O', 'P', 'E'})
                        .build()));

        assertEquals(INVOICE_FILE_CONTENT_INVALID, exception.getCode());
        verifyNoInteractions(fileStorageService, invoiceMapper, attachmentMapper);
    }

}
