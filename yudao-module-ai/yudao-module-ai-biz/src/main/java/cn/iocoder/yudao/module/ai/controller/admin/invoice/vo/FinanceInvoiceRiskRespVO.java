package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import lombok.Data;

import java.util.List;

/**
 * Finance invoice risk response.
 */
@Data
public class FinanceInvoiceRiskRespVO {

    private String riskLevel;
    private List<String> riskFlags;
    private String riskSummary;

}
