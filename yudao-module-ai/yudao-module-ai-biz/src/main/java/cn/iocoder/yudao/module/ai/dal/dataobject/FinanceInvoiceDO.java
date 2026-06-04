package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Finance invoice DO.
 */
@TableName("finance_invoice")
@Data
public class FinanceInvoiceDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("invoice_code")
    private String invoiceCode;
    @TableField("source_type")
    private String sourceType;
    @TableField("source_message_id")
    private String sourceMessageId;
    @TableField("source_sender")
    private String sourceSender;
    @TableField("source_received_time")
    private LocalDateTime sourceReceivedTime;
    @TableField("file_id")
    private Long fileId;
    @TableField("file_url")
    private String fileUrl;
    @TableField("object_key")
    private String objectKey;
    @TableField("file_name")
    private String fileName;
    @TableField("file_type")
    private String fileType;
    @TableField("file_hash")
    private String fileHash;
    @TableField("file_size")
    private Long fileSize;

    @TableField("supplier_name")
    private String supplierName;
    @TableField("supplier_tax_no")
    private String supplierTaxNo;
    @TableField("invoice_no")
    private String invoiceNo;
    @TableField("invoice_date")
    private LocalDateTime invoiceDate;
    @TableField("due_date")
    private LocalDateTime dueDate;
    @TableField("currency")
    private String currency;
    @TableField("net_amount")
    private BigDecimal netAmount;
    @TableField("vat_amount")
    private BigDecimal vatAmount;
    @TableField("gross_amount")
    private BigDecimal grossAmount;
    @TableField("iban")
    private String iban;
    @TableField("bic")
    private String bic;
    @TableField("payment_account_name")
    private String paymentAccountName;

    @TableField("expense_category")
    private String expenseCategory;
    @TableField("business_desc")
    private String businessDesc;
    @TableField("po_no")
    private String poNo;
    @TableField("contract_no")
    private String contractNo;
    @TableField("project_name")
    private String projectName;

    @TableField("ai_status")
    private String aiStatus;
    @TableField("ai_confidence")
    private BigDecimal aiConfidence;
    @TableField("ai_raw_result")
    private String aiRawResult;
    @TableField("ai_summary")
    private String aiSummary;
    @TableField("ai_error_message")
    private String aiErrorMessage;

    @TableField("finance_review_status")
    private String financeReviewStatus;
    @TableField("risk_level")
    private String riskLevel;
    @TableField("risk_flags")
    private String riskFlags;
    @TableField("risk_summary")
    private String riskSummary;

    @TableField("approval_status")
    private String approvalStatus;
    @TableField("process_instance_id")
    private String processInstanceId;
    @TableField("process_definition_key")
    private String processDefinitionKey;

    @TableField("bookkeeping_status")
    private String bookkeepingStatus;
    @TableField("payment_status")
    private String paymentStatus;
    @TableField("payment_time")
    private LocalDateTime paymentTime;
    @TableField("payment_remark")
    private String paymentRemark;

    @TableField("remark")
    private String remark;
    @TableField("creator")
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("updater")
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;
    @TableField("tenant_id")
    private Long tenantId;

}
