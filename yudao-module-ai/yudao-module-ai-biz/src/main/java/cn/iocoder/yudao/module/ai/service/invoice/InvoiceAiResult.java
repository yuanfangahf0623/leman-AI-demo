package cn.iocoder.yudao.module.ai.service.invoice;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Invoice AI recognition result.
 */
@Data
public class InvoiceAiResult {

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
    private BigDecimal confidence;
    private String summary;
    private String rawJson;
    private String errorMessage;

    public boolean success() {
        return errorMessage == null || errorMessage.isBlank();
    }

}
