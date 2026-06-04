package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceConfirmReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoicePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceSubmitApprovalReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdateBookkeepingReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdatePaymentReqVO;
import cn.iocoder.yudao.module.ai.convert.FinanceInvoiceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceAttachmentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceMapper;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_AI_RECOGNIZE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_DUPLICATE_RISK_BLOCKED;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_CONTENT_INVALID;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_EMPTY;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_NAME_INVALID;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_STORAGE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_FILE_TYPE_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_PAYMENT_INFO_INVALID;
import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_STATUS_INVALID;

/**
 * Finance invoice service implementation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceInvoiceServiceImpl implements FinanceInvoiceService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final FinanceInvoiceMapper invoiceMapper;
    private final FinanceInvoiceAttachmentMapper attachmentMapper;
    private final FinanceInvoiceAiService invoiceAiService;
    private final FinanceInvoiceRiskService invoiceRiskService;
    private final FinanceInvoiceApprovalService invoiceApprovalService;
    private final FinanceSupplierPaymentInfoService supplierPaymentInfoService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Override
    public Long uploadInvoice(MultipartFile file) {
        validateMultipartFileNotEmpty(file);
        return createInvoiceFromSource(FinanceInvoiceSourceFile.builder()
                .sourceType(FinanceInvoiceConstants.SOURCE_UPLOAD)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .content(readFileContent(file))
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInvoiceFromSource(FinanceInvoiceSourceFile sourceFile) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateSourceFile(sourceFile);
        String fileName = sanitizeFileName(sourceFile.getFileName());
        String extension = getSupportedExtension(fileName);
        byte[] content = sourceFile.getContent();
        validateFileContent(extension, content);
        String objectKey = buildObjectKey(tenantId, sourceFile.getSourceType(), extension);
        FileStorageResult storageResult = fileStorageService.store(objectKey, content);

        FinanceInvoiceDO invoice = new FinanceInvoiceDO();
        invoice.setTenantId(tenantId);
        invoice.setSourceType(normalizeSourceType(sourceFile.getSourceType()));
        invoice.setSourceMessageId(trimToNull(sourceFile.getSourceMessageId()));
        invoice.setSourceSender(trimToNull(sourceFile.getSourceSender()));
        invoice.setSourceReceivedTime(sourceFile.getSourceReceivedTime());
        invoice.setFileUrl(storageResult.getSourceUri());
        invoice.setObjectKey(storageResult.getObjectKey());
        invoice.setFileName(fileName);
        invoice.setFileType(extension);
        invoice.setFileSize((long) content.length);
        invoice.setFileHash(sha256Hex(content));
        invoice.setAiStatus(FinanceInvoiceConstants.AI_STATUS_NOT_RECOGNIZED);
        invoice.setFinanceReviewStatus(FinanceInvoiceConstants.REVIEW_STATUS_PENDING);
        invoice.setRiskLevel(FinanceInvoiceConstants.RISK_LEVEL_NONE);
        invoice.setRiskFlags("[]");
        invoice.setApprovalStatus(FinanceInvoiceConstants.APPROVAL_STATUS_DRAFT);
        invoice.setBookkeepingStatus(FinanceInvoiceConstants.BOOKKEEPING_STATUS_NOT_BOOKED);
        invoice.setPaymentStatus(FinanceInvoiceConstants.PAYMENT_STATUS_NOT_PAID);
        invoice.setRemark(trimToNull(sourceFile.getRemark()));
        invoiceMapper.insert(invoice);

        FinanceInvoiceDO codeUpdate = new FinanceInvoiceDO();
        codeUpdate.setId(invoice.getId());
        codeUpdate.setInvoiceCode(buildInvoiceCode(invoice.getId()));
        invoiceMapper.updateByIdAndTenantId(codeUpdate, tenantId);

        FinanceInvoiceAttachmentDO attachment = new FinanceInvoiceAttachmentDO();
        attachment.setTenantId(tenantId);
        attachment.setInvoiceId(invoice.getId());
        attachment.setFileName(fileName);
        attachment.setFileUrl(storageResult.getSourceUri());
        attachment.setObjectKey(storageResult.getObjectKey());
        attachment.setFileType(extension);
        attachment.setFileSize((long) content.length);
        attachment.setFileHash(invoice.getFileHash());
        attachment.setAttachmentType(FinanceInvoiceConstants.ATTACHMENT_TYPE_INVOICE);
        attachmentMapper.insert(attachment);
        return invoice.getId();
    }

    @Override
    public FinanceInvoiceDO getInvoice(Long id) {
        return validateInvoiceExists(id);
    }

    @Override
    public List<FinanceInvoiceAttachmentDO> getInvoiceAttachments(Long invoiceId) {
        FinanceInvoiceDO invoice = validateInvoiceExists(invoiceId);
        return attachmentMapper.selectListByInvoiceIdAndTenantId(invoiceId, invoice.getTenantId());
    }

    @Override
    public FinanceInvoicePreview getInvoicePreview(Long id) {
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        return new FinanceInvoicePreview(invoice.getFileName(), resolveContentType(invoice),
                fileStorageService.load(invoice.getObjectKey()));
    }

    @Override
    public PageResult<FinanceInvoiceDO> getInvoicePage(FinanceInvoicePageReqVO pageReqVO) {
        return invoiceMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceInvoiceDO recognizeInvoice(Long id, boolean force) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        validateRecognizeStatus(invoice, force);
        updateAiStatus(id, tenantId, FinanceInvoiceConstants.AI_STATUS_RECOGNIZING, null);
        try {
            InvoiceAiResult aiResult = invoiceAiService.recognizeInvoice(invoice.getId(), invoice.getFileUrl(),
                    invoice.getFileType());
            FinanceInvoiceDO updateObj = FinanceInvoiceConvert.INSTANCE.convertAiResult(aiResult);
            updateObj.setId(id);
            if (aiResult.success()) {
                updateObj.setAiStatus(FinanceInvoiceConstants.AI_STATUS_RECOGNIZED);
                updateObj.setApprovalStatus(FinanceInvoiceConstants.APPROVAL_STATUS_WAIT_CONFIRM);
            } else {
                updateObj.setAiStatus(FinanceInvoiceConstants.AI_STATUS_FAILED);
                updateObj.setApprovalStatus(FinanceInvoiceConstants.APPROVAL_STATUS_DRAFT);
            }
            invoiceMapper.updateByIdAndTenantId(updateObj, tenantId);
            return validateInvoiceExists(id);
        } catch (Exception ex) {
            updateAiStatus(id, tenantId, FinanceInvoiceConstants.AI_STATUS_FAILED, safeErrorMessage(ex));
            log.warn("Invoice AI recognition failed, invoiceId={}, tenantId={}, reason={}", id, tenantId,
                    safeErrorMessage(ex));
            throw new ServiceException(INVOICE_AI_RECOGNIZE_FAILED, "Invoice AI recognition failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceRiskResult confirmInvoice(Long id, FinanceInvoiceConfirmReqVO reqVO) {
        FinanceInvoiceDO oldInvoice = validateInvoiceExists(id);
        validateEditableStatus(oldInvoice);
        FinanceInvoiceDO updateObj = FinanceInvoiceConvert.INSTANCE.convertConfirm(reqVO);
        updateObj.setId(id);
        updateObj.setFinanceReviewStatus(FinanceInvoiceConstants.REVIEW_STATUS_CONFIRMED);
        if (FinanceInvoiceConstants.APPROVAL_STATUS_DRAFT.equals(oldInvoice.getApprovalStatus())
                || FinanceInvoiceConstants.APPROVAL_STATUS_REJECTED.equals(oldInvoice.getApprovalStatus())) {
            updateObj.setApprovalStatus(FinanceInvoiceConstants.APPROVAL_STATUS_WAIT_CONFIRM);
        }
        invoiceMapper.updateByIdAndTenantId(updateObj, oldInvoice.getTenantId());

        InvoiceRiskResult riskResult = invoiceRiskService.detectRisks(id);
        FinanceInvoiceDO riskUpdate = new FinanceInvoiceDO();
        riskUpdate.setId(id);
        riskUpdate.setRiskLevel(riskResult.getRiskLevel());
        riskUpdate.setRiskFlags(toJson(riskResult.getRiskFlags()));
        riskUpdate.setRiskSummary(riskResult.getRiskSummary());
        invoiceMapper.updateByIdAndTenantId(riskUpdate, oldInvoice.getTenantId());

        FinanceInvoiceDO confirmedInvoice = validateInvoiceExists(id);
        supplierPaymentInfoService.updateFromConfirmedInvoice(confirmedInvoice);
        return riskResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitApproval(Long id, FinanceInvoiceSubmitApprovalReqVO reqVO) {
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        validateSubmitApprovalStatus(invoice);
        if (invoice.getRiskFlags() == null || invoice.getRiskFlags().isBlank()) {
            InvoiceRiskResult riskResult = invoiceRiskService.detectRisks(id);
            FinanceInvoiceDO riskUpdate = new FinanceInvoiceDO();
            riskUpdate.setId(id);
            riskUpdate.setRiskLevel(riskResult.getRiskLevel());
            riskUpdate.setRiskFlags(toJson(riskResult.getRiskFlags()));
            riskUpdate.setRiskSummary(riskResult.getRiskSummary());
            invoiceMapper.updateByIdAndTenantId(riskUpdate, invoice.getTenantId());
            invoice = validateInvoiceExists(id);
        }
        if (containsRiskFlag(invoice.getRiskFlags(), FinanceInvoiceConstants.RISK_FLAG_DUPLICATE_INVOICE)) {
            throw new ServiceException(INVOICE_DUPLICATE_RISK_BLOCKED, "Suspected duplicate invoice, please review manually before submitting");
        }
        String processDefinitionKey = resolveProcessDefinitionKey(reqVO);
        FinanceInvoiceDO processKeyUpdate = new FinanceInvoiceDO();
        processKeyUpdate.setId(id);
        processKeyUpdate.setProcessDefinitionKey(processDefinitionKey);
        invoiceMapper.updateByIdAndTenantId(processKeyUpdate, invoice.getTenantId());
        invoice = validateInvoiceExists(id);

        String processInstanceId = invoiceApprovalService.submitApproval(invoice, reqVO);
        FinanceInvoiceDO updateObj = new FinanceInvoiceDO();
        updateObj.setId(id);
        updateObj.setProcessInstanceId(processInstanceId);
        updateObj.setApprovalStatus(FinanceInvoiceConstants.APPROVAL_STATUS_APPROVING);
        invoiceMapper.updateByIdAndTenantId(updateObj, invoice.getTenantId());
        return processInstanceId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePayment(Long id, FinanceInvoiceUpdatePaymentReqVO reqVO) {
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        validateApproved(invoice);
        if (!Set.of(FinanceInvoiceConstants.PAYMENT_STATUS_NOT_PAID, FinanceInvoiceConstants.PAYMENT_STATUS_PAID)
                .contains(reqVO.getPaymentStatus())) {
            throw new ServiceException(INVOICE_PAYMENT_INFO_INVALID, "Payment status is invalid");
        }
        FinanceInvoiceDO updateObj = new FinanceInvoiceDO();
        updateObj.setId(id);
        updateObj.setPaymentStatus(reqVO.getPaymentStatus());
        updateObj.setPaymentTime(FinanceInvoiceConstants.PAYMENT_STATUS_PAID.equals(reqVO.getPaymentStatus())
                ? (reqVO.getPaymentTime() == null ? LocalDateTime.now() : reqVO.getPaymentTime())
                : reqVO.getPaymentTime());
        updateObj.setPaymentRemark(reqVO.getPaymentRemark());
        invoiceMapper.updateByIdAndTenantId(updateObj, invoice.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBookkeeping(Long id, FinanceInvoiceUpdateBookkeepingReqVO reqVO) {
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        validateApproved(invoice);
        if (!Set.of(FinanceInvoiceConstants.BOOKKEEPING_STATUS_NOT_BOOKED,
                FinanceInvoiceConstants.BOOKKEEPING_STATUS_BOOKED).contains(reqVO.getBookkeepingStatus())) {
            throw new ServiceException(INVOICE_PAYMENT_INFO_INVALID, "Bookkeeping status is invalid");
        }
        FinanceInvoiceDO updateObj = new FinanceInvoiceDO();
        updateObj.setId(id);
        updateObj.setBookkeepingStatus(reqVO.getBookkeepingStatus());
        invoiceMapper.updateByIdAndTenantId(updateObj, invoice.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInvoice(Long id) {
        FinanceInvoiceDO invoice = validateInvoiceExists(id);
        if (FinanceInvoiceConstants.APPROVAL_STATUS_APPROVING.equals(invoice.getApprovalStatus())
                || FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(invoice.getApprovalStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Approving or approved invoice cannot be deleted");
        }
        invoiceMapper.deleteByIdAndTenantId(id, invoice.getTenantId());
    }

    @Override
    public byte[] exportInvoices(FinanceInvoicePageReqVO pageReqVO) {
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(100);
        List<FinanceInvoiceDO> invoices = getInvoicePage(pageReqVO).getList();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("finance-invoice");
            CellStyle dateTimeStyle = workbook.createCellStyle();
            CreationHelper creationHelper = workbook.getCreationHelper();
            dateTimeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            Row header = sheet.createRow(0);
            String[] titles = {"系统编号", "供应商", "发票号", "发票日期", "币种", "含税金额", "费用类别", "风险等级", "审批状态", "记账状态", "付款状态", "创建时间"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            for (int rowIndex = 0; rowIndex < invoices.size(); rowIndex++) {
                writeInvoiceRow(sheet.createRow(rowIndex + 1), invoices.get(rowIndex), dateTimeStyle);
            }
            for (int i = 0; i < titles.length; i++) {
                sheet.setColumnWidth(i, 20 * 256);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ServiceException(INVOICE_FILE_STORAGE_FAILED, "Export invoices failed");
        }
    }

    private void validateSourceFile(FinanceInvoiceSourceFile sourceFile) {
        if (sourceFile == null || sourceFile.getContent() == null || sourceFile.getContent().length == 0) {
            throw new ServiceException(INVOICE_FILE_EMPTY, "Invoice file is required");
        }
        Integer maxFileSizeMb = aiProperties.getInvoice().getMaxFileSizeMb();
        long maxFileSizeBytes = (maxFileSizeMb == null || maxFileSizeMb <= 0 ? 20L : maxFileSizeMb.longValue())
                * 1024L * 1024L;
        if (sourceFile.getContent().length > maxFileSizeBytes) {
            throw new ServiceException(INVOICE_FILE_TOO_LARGE, "Invoice file size exceeds limit");
        }
    }

    private void validateMultipartFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(INVOICE_FILE_EMPTY, "Invoice file is required");
        }
    }

    private byte[] readFileContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ServiceException(INVOICE_FILE_STORAGE_FAILED, "Read invoice file failed");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ServiceException(INVOICE_FILE_NAME_INVALID, "Invoice file name is required");
        }
        String normalized = originalFilename.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.contains("..") || hasControlChar(fileName)) {
            throw new ServiceException(INVOICE_FILE_NAME_INVALID, "Invoice file name is invalid");
        }
        return fileName;
    }

    private String getSupportedExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ServiceException(INVOICE_FILE_TYPE_UNSUPPORTED, "Invoice file type is unsupported");
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!FinanceInvoiceConstants.UPLOAD_ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(INVOICE_FILE_TYPE_UNSUPPORTED, "Invoice file type is unsupported");
        }
        return extension;
    }

    private void validateFileContent(String extension, byte[] content) {
        if ("pdf".equals(extension)) {
            if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                    || content[3] != 'F' || content[4] != '-') {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "PDF content is invalid");
            }
            return;
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            if (content.length < 3 || (content[0] & 0xff) != 0xff || (content[1] & 0xff) != 0xd8
                    || (content[2] & 0xff) != 0xff) {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "JPEG content is invalid");
            }
            return;
        }
        if ("png".equals(extension)) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (content.length < signature.length) {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "PNG content is invalid");
            }
            for (int i = 0; i < signature.length; i++) {
                if (content[i] != signature[i]) {
                    throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "PNG content is invalid");
                }
            }
            return;
        }
        if ("tif".equals(extension) || "tiff".equals(extension)) {
            boolean littleEndian = content.length >= 4 && content[0] == 'I' && content[1] == 'I'
                    && content[2] == 42 && content[3] == 0;
            boolean bigEndian = content.length >= 4 && content[0] == 'M' && content[1] == 'M'
                    && content[2] == 0 && content[3] == 42;
            if (!littleEndian && !bigEndian) {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "TIFF content is invalid");
            }
            return;
        }
        if ("docx".equals(extension)) {
            if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "DOCX content is invalid");
            }
            return;
        }
        if ("doc".equals(extension)) {
            if (content.length < 8 || (content[0] & 0xff) != 0xd0 || (content[1] & 0xff) != 0xcf
                    || (content[2] & 0xff) != 0x11 || (content[3] & 0xff) != 0xe0
                    || (content[4] & 0xff) != 0xa1 || (content[5] & 0xff) != 0xb1
                    || (content[6] & 0xff) != 0x1a || (content[7] & 0xff) != 0xe1) {
                throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "DOC content is invalid");
            }
            return;
        }
        throw new ServiceException(INVOICE_FILE_TYPE_UNSUPPORTED, "Invoice file type is unsupported");
    }

    private FinanceInvoiceDO validateInvoiceExists(Long id) {
        FinanceInvoiceDO invoice = invoiceMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
        if (invoice == null) {
            throw new ServiceException(INVOICE_NOT_EXISTS, "Invoice does not exist");
        }
        return invoice;
    }

    private void validateRecognizeStatus(FinanceInvoiceDO invoice, boolean force) {
        if (FinanceInvoiceConstants.APPROVAL_STATUS_APPROVING.equals(invoice.getApprovalStatus())
                || FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(invoice.getApprovalStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Approving or approved invoice cannot be recognized");
        }
        if (!force && FinanceInvoiceConstants.AI_STATUS_RECOGNIZING.equals(invoice.getAiStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Invoice is recognizing");
        }
    }

    private void validateEditableStatus(FinanceInvoiceDO invoice) {
        if (FinanceInvoiceConstants.APPROVAL_STATUS_APPROVING.equals(invoice.getApprovalStatus())
                || FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(invoice.getApprovalStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Approving or approved invoice cannot be edited");
        }
    }

    private void validateSubmitApprovalStatus(FinanceInvoiceDO invoice) {
        if (!FinanceInvoiceConstants.REVIEW_STATUS_CONFIRMED.equals(invoice.getFinanceReviewStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Invoice must be confirmed before submitting approval");
        }
        if (FinanceInvoiceConstants.APPROVAL_STATUS_APPROVING.equals(invoice.getApprovalStatus())
                || FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(invoice.getApprovalStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Invoice is already approving or approved");
        }
    }

    private void validateApproved(FinanceInvoiceDO invoice) {
        if (!FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(invoice.getApprovalStatus())) {
            throw new ServiceException(INVOICE_STATUS_INVALID, "Only approved invoice can be updated");
        }
    }

    private void updateAiStatus(Long id, Long tenantId, String aiStatus, String errorMessage) {
        FinanceInvoiceDO updateObj = new FinanceInvoiceDO();
        updateObj.setId(id);
        updateObj.setAiStatus(aiStatus);
        updateObj.setAiErrorMessage(errorMessage);
        invoiceMapper.updateByIdAndTenantId(updateObj, tenantId);
    }

    private String resolveProcessDefinitionKey(FinanceInvoiceSubmitApprovalReqVO reqVO) {
        if (reqVO != null && reqVO.getProcessDefinitionKey() != null && !reqVO.getProcessDefinitionKey().isBlank()) {
            return reqVO.getProcessDefinitionKey().trim();
        }
        String configured = aiProperties.getInvoice().getProcessDefinitionKey();
        return configured == null || configured.isBlank() ? "finance_invoice_approval" : configured.trim();
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return FinanceInvoiceConstants.SOURCE_UPLOAD;
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private String buildObjectKey(Long tenantId, String sourceType, String extension) {
        return "finance/invoice/" + tenantId + "/" + normalizeSourceType(sourceType).toLowerCase(Locale.ROOT)
                + "/" + UUID.randomUUID() + "." + extension;
    }

    private String buildInvoiceCode(Long id) {
        return "FINV-" + Year.now().getValue() + "-" + String.format("%06d", id);
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
            throw new ServiceException(INVOICE_FILE_CONTENT_INVALID, "Calculate invoice file hash failed");
        }
    }

    private String resolveContentType(FinanceInvoiceDO invoice) {
        return switch (invoice.getFileType() == null ? "" : invoice.getFileType().toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tif", "tiff" -> "image/tiff";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private boolean containsRiskFlag(String riskFlagsJson, String riskFlag) {
        return riskFlagsJson != null && riskFlagsJson.contains("\"" + riskFlag + "\"");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
    }

    private boolean hasControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void writeInvoiceRow(Row row, FinanceInvoiceDO invoice, CellStyle dateTimeStyle) {
        writeCell(row.createCell(0), invoice.getInvoiceCode(), dateTimeStyle);
        writeCell(row.createCell(1), invoice.getSupplierName(), dateTimeStyle);
        writeCell(row.createCell(2), invoice.getInvoiceNo(), dateTimeStyle);
        writeCell(row.createCell(3), invoice.getInvoiceDate(), dateTimeStyle);
        writeCell(row.createCell(4), invoice.getCurrency(), dateTimeStyle);
        writeCell(row.createCell(5), invoice.getGrossAmount(), dateTimeStyle);
        writeCell(row.createCell(6), invoice.getExpenseCategory(), dateTimeStyle);
        writeCell(row.createCell(7), invoice.getRiskLevel(), dateTimeStyle);
        writeCell(row.createCell(8), invoice.getApprovalStatus(), dateTimeStyle);
        writeCell(row.createCell(9), invoice.getBookkeepingStatus(), dateTimeStyle);
        writeCell(row.createCell(10), invoice.getPaymentStatus(), dateTimeStyle);
        writeCell(row.createCell(11), invoice.getCreateTime(), dateTimeStyle);
    }

    private void writeCell(Cell cell, Object value, CellStyle dateTimeStyle) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(dateTimeStyle);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

}
