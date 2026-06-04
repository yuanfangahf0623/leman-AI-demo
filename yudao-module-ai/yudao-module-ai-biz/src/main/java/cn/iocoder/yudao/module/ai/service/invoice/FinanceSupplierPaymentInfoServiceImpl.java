package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceSupplierPaymentInfoDO;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceSupplierPaymentInfoMapper;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Supplier payment info service implementation.
 */
@Service
@RequiredArgsConstructor
public class FinanceSupplierPaymentInfoServiceImpl implements FinanceSupplierPaymentInfoService {

    private final FinanceSupplierPaymentInfoMapper supplierPaymentInfoMapper;

    @Override
    public void updateFromConfirmedInvoice(FinanceInvoiceDO invoice) {
        if (invoice == null || isBlank(invoice.getSupplierName())) {
            return;
        }
        FinanceSupplierPaymentInfoDO paymentInfo = findExisting(invoice);
        if (paymentInfo == null) {
            paymentInfo = new FinanceSupplierPaymentInfoDO();
            paymentInfo.setTenantId(invoice.getTenantId());
            paymentInfo.setSupplierName(invoice.getSupplierName());
            paymentInfo.setSupplierTaxNo(invoice.getSupplierTaxNo());
            paymentInfo.setIban(trimToNull(invoice.getIban()));
            paymentInfo.setBic(trimToNull(invoice.getBic()));
            paymentInfo.setPaymentAccountName(invoice.getPaymentAccountName());
            paymentInfo.setFirstInvoiceId(invoice.getId());
            paymentInfo.setLastInvoiceId(invoice.getId());
            paymentInfo.setLastUsedTime(LocalDateTime.now());
            paymentInfo.setStatus(FinanceInvoiceConstants.SUPPLIER_PAYMENT_STATUS_ENABLED);
            supplierPaymentInfoMapper.insert(paymentInfo);
            return;
        }
        paymentInfo.setSupplierTaxNo(invoice.getSupplierTaxNo());
        paymentInfo.setBic(trimToNull(invoice.getBic()));
        paymentInfo.setPaymentAccountName(invoice.getPaymentAccountName());
        paymentInfo.setLastInvoiceId(invoice.getId());
        paymentInfo.setLastUsedTime(LocalDateTime.now());
        supplierPaymentInfoMapper.updateByIdAndTenantId(paymentInfo, invoice.getTenantId());
    }

    private FinanceSupplierPaymentInfoDO findExisting(FinanceInvoiceDO invoice) {
        if (!isBlank(invoice.getIban())) {
            FinanceSupplierPaymentInfoDO byIban = supplierPaymentInfoMapper.selectBySupplierNameAndIban(
                    invoice.getTenantId(), invoice.getSupplierName(), invoice.getIban());
            if (byIban != null) {
                return byIban;
            }
        }
        return supplierPaymentInfoMapper.selectListBySupplierName(invoice.getTenantId(), invoice.getSupplierName())
                .stream()
                .filter(item -> isBlank(item.getIban()) && isBlank(invoice.getIban()))
                .findFirst()
                .orElse(null);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
