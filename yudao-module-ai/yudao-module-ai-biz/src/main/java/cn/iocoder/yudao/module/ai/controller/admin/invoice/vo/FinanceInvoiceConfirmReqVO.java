package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Finance invoice confirm request.
 */
@Data
public class FinanceInvoiceConfirmReqVO {

    @NotBlank(message = "supplier name is required")
    @Size(max = 255, message = "supplier name length must be <= 255")
    private String supplierName;

    @Size(max = 128, message = "supplier tax no length must be <= 128")
    private String supplierTaxNo;

    @NotBlank(message = "invoice no is required")
    @Size(max = 128, message = "invoice no length must be <= 128")
    private String invoiceNo;

    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;

    @NotBlank(message = "currency is required")
    @Size(max = 16, message = "currency length must be <= 16")
    private String currency;

    @DecimalMin(value = "0.00", inclusive = true, message = "net amount must be >= 0")
    private BigDecimal netAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "vat amount must be >= 0")
    private BigDecimal vatAmount;

    @NotNull(message = "gross amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "gross amount must be > 0")
    private BigDecimal grossAmount;

    @Size(max = 64, message = "iban length must be <= 64")
    private String iban;

    @Size(max = 64, message = "bic length must be <= 64")
    private String bic;

    @Size(max = 255, message = "payment account name length must be <= 255")
    private String paymentAccountName;

    @Size(max = 64, message = "expense category length must be <= 64")
    private String expenseCategory;

    @Size(max = 1000, message = "business desc length must be <= 1000")
    private String businessDesc;

    @Size(max = 128, message = "po no length must be <= 128")
    private String poNo;

    @Size(max = 128, message = "contract no length must be <= 128")
    private String contractNo;

    @Size(max = 255, message = "project name length must be <= 255")
    private String projectName;

    @Size(max = 1000, message = "remark length must be <= 1000")
    private String remark;

}
