package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Supplier payment info DO.
 */
@TableName("finance_supplier_payment_info")
@Data
public class FinanceSupplierPaymentInfoDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("supplier_name")
    private String supplierName;
    @TableField("supplier_tax_no")
    private String supplierTaxNo;
    @TableField("iban")
    private String iban;
    @TableField("bic")
    private String bic;
    @TableField("payment_account_name")
    private String paymentAccountName;
    @TableField("first_invoice_id")
    private Long firstInvoiceId;
    @TableField("last_invoice_id")
    private Long lastInvoiceId;
    @TableField("last_used_time")
    private LocalDateTime lastUsedTime;
    @TableField("status")
    private String status;
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
