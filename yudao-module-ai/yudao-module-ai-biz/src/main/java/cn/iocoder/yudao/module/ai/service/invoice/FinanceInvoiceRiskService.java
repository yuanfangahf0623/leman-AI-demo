package cn.iocoder.yudao.module.ai.service.invoice;

/**
 * Finance invoice risk service.
 */
public interface FinanceInvoiceRiskService {

    InvoiceRiskResult detectRisks(Long invoiceId);

}
