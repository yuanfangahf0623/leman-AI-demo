package cn.iocoder.yudao.module.ai.controller.admin.invoice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

/**
 * Finance invoice page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceInvoicePageReqVO extends PageParam {

    private String supplierName;
    private String invoiceNo;
    private String approvalStatus;
    private String aiStatus;
    private String financeReviewStatus;
    private String paymentStatus;
    private String bookkeepingStatus;
    private String riskLevel;

    @DateTimeFormat(iso = DATE_TIME)
    private LocalDateTime invoiceDateStart;
    @DateTimeFormat(iso = DATE_TIME)
    private LocalDateTime invoiceDateEnd;
    @DateTimeFormat(iso = DATE_TIME)
    private LocalDateTime createTimeStart;
    @DateTimeFormat(iso = DATE_TIME)
    private LocalDateTime createTimeEnd;

}
