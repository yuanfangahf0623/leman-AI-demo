package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Finance invoice response.
 */
@Data
public class FinanceInvoiceRespVO {

    private Long id;
    private String invoiceCode;
    private String sourceType;
    private String sourceMessageId;
    private String sourceSender;
    private LocalDateTime sourceReceivedTime;
    private Long fileId;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private String fileHash;
    private Long fileSize;

    private String supplierName;
    private String supplierTaxNo;
    private String invoiceNo;
    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;
    private String currency;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private String iban;
    private String bic;
    private String paymentAccountName;

    private String expenseCategory;
    private String businessDesc;
    private String poNo;
    private String contractNo;
    private String projectName;

    private String aiStatus;
    private BigDecimal aiConfidence;
    private String aiRawResult;
    private String aiSummary;
    private String aiErrorMessage;

    private String financeReviewStatus;
    private String riskLevel;
    private String riskFlags;
    private String riskSummary;

    private String approvalStatus;
    private String processInstanceId;
    private String processDefinitionKey;

    private String bookkeepingStatus;
    private String paymentStatus;
    private LocalDateTime paymentTime;
    private String paymentRemark;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<AttachmentRespVO> attachments;

    @Data
    public static class AttachmentRespVO {

        private Long id;
        private String fileName;
        private String fileUrl;
        private String fileType;
        private Long fileSize;
        private String fileHash;
        private String attachmentType;
        private LocalDateTime createTime;

    }

}
