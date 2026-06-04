package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Finance invoice approval result request.
 */
@Data
public class FinanceInvoiceApprovalResultReqVO {

    @NotBlank(message = "process instance id is required")
    private String processInstanceId;

    @NotBlank(message = "approval status is required")
    private String approvalStatus;

}
