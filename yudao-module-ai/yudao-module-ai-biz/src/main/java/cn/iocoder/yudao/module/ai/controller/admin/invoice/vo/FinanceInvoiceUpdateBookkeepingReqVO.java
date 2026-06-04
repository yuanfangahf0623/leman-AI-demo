package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Finance invoice bookkeeping update request.
 */
@Data
public class FinanceInvoiceUpdateBookkeepingReqVO {

    @NotBlank(message = "bookkeeping status is required")
    private String bookkeepingStatus;

}
