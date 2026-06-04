package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Finance invoice submit approval request.
 */
@Data
public class FinanceInvoiceSubmitApprovalReqVO {

    private String processDefinitionKey;

    private Map<String, List<Long>> startUserSelectAssignees;

}
