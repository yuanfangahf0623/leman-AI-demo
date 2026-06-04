package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;

/**
 * Supplier payment info service.
 */
public interface FinanceSupplierPaymentInfoService {

    void updateFromConfirmedInvoice(FinanceInvoiceDO invoice);

}
