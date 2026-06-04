package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Finance invoice update request.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class FinanceInvoiceUpdateReqVO extends FinanceInvoiceConfirmReqVO {

    @NotNull(message = "invoice id is required")
    private Long id;

}
