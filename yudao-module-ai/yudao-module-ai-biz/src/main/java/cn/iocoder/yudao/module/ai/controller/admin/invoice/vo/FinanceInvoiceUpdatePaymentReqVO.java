package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Finance invoice payment update request.
 */
@Data
public class FinanceInvoiceUpdatePaymentReqVO {

    @NotBlank(message = "payment status is required")
    private String paymentStatus;

    private LocalDateTime paymentTime;

    @Size(max = 1000, message = "payment remark length must be <= 1000")
    private String paymentRemark;

}
