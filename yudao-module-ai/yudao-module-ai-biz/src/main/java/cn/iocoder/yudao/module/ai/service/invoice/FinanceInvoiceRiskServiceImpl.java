package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceSupplierPaymentInfoDO;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceSupplierPaymentInfoMapper;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_NOT_EXISTS;

/**
 * Finance invoice risk service implementation.
 */
@Service
@RequiredArgsConstructor
public class FinanceInvoiceRiskServiceImpl implements FinanceInvoiceRiskService {

    private static final BigDecimal AMOUNT_LIMIT_EUR = new BigDecimal("3000.00");
    private static final BigDecimal VAT_TOLERANCE = new BigDecimal("0.05");
    private static final Map<String, String> RISK_LABELS = buildRiskLabels();

    private final FinanceInvoiceMapper invoiceMapper;
    private final FinanceSupplierPaymentInfoMapper supplierPaymentInfoMapper;

    @Override
    public InvoiceRiskResult detectRisks(Long invoiceId) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        FinanceInvoiceDO invoice = invoiceMapper.selectByIdAndTenantId(invoiceId, tenantId);
        if (invoice == null) {
            throw new ServiceException(INVOICE_NOT_EXISTS, "Invoice does not exist");
        }
        List<String> flags = new ArrayList<>();
        String riskLevel = FinanceInvoiceConstants.RISK_LEVEL_NONE;

        if (isDuplicateInvoice(invoice, tenantId)) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_DUPLICATE_INVOICE);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_HIGH);
        }
        if (isNewSupplier(invoice, tenantId)) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_NEW_SUPPLIER);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_MEDIUM);
        }
        if (isIbanChanged(invoice, tenantId)) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_IBAN_CHANGED);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_HIGH);
        }
        if (isBlank(invoice.getPoNo())) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_NO_PO);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_MEDIUM);
        }
        if (isBlank(invoice.getContractNo())) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_NO_CONTRACT);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_LOW);
        }
        if (isAmountOverLimit(invoice)) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_AMOUNT_OVER_LIMIT);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_HIGH);
        }
        if (isVatAbnormal(invoice)) {
            flags.add(FinanceInvoiceConstants.RISK_FLAG_VAT_ABNORMAL);
            riskLevel = maxRiskLevel(riskLevel, FinanceInvoiceConstants.RISK_LEVEL_MEDIUM);
        }
        return new InvoiceRiskResult(riskLevel, flags, buildRiskSummary(flags, riskLevel));
    }

    private boolean isDuplicateInvoice(FinanceInvoiceDO invoice, Long tenantId) {
        return invoiceMapper.selectDuplicateCount(tenantId, invoice.getId(), invoice.getSupplierName(),
                invoice.getInvoiceNo(), invoice.getGrossAmount(), invoice.getInvoiceDate()) > 0;
    }

    private boolean isNewSupplier(FinanceInvoiceDO invoice, Long tenantId) {
        if (isBlank(invoice.getSupplierName())) {
            return false;
        }
        Long paymentInfoCount = supplierPaymentInfoMapper.selectCountBySupplierName(tenantId, invoice.getSupplierName());
        Long invoiceHistoryCount = invoiceMapper.selectSupplierHistoryCount(tenantId, invoice.getId(),
                invoice.getSupplierName());
        return paymentInfoCount == 0 && invoiceHistoryCount == 0;
    }

    private boolean isIbanChanged(FinanceInvoiceDO invoice, Long tenantId) {
        if (isBlank(invoice.getSupplierName()) || isBlank(invoice.getIban())) {
            return false;
        }
        String currentIban = invoice.getIban().trim();
        boolean paymentInfoChanged = supplierPaymentInfoMapper.selectListBySupplierName(tenantId, invoice.getSupplierName())
                .stream()
                .map(FinanceSupplierPaymentInfoDO::getIban)
                .filter(value -> !isBlank(value))
                .anyMatch(oldIban -> !currentIban.equalsIgnoreCase(oldIban.trim()));
        if (paymentInfoChanged) {
            return true;
        }
        return invoiceMapper.selectSupplierIbanHistory(tenantId, invoice.getId(), invoice.getSupplierName()).stream()
                .map(FinanceInvoiceDO::getIban)
                .filter(value -> !isBlank(value))
                .anyMatch(oldIban -> !currentIban.equalsIgnoreCase(oldIban.trim()));
    }

    private boolean isAmountOverLimit(FinanceInvoiceDO invoice) {
        return "EUR".equalsIgnoreCase(trimToEmpty(invoice.getCurrency()))
                && invoice.getGrossAmount() != null
                && invoice.getGrossAmount().compareTo(AMOUNT_LIMIT_EUR) > 0;
    }

    private boolean isVatAbnormal(FinanceInvoiceDO invoice) {
        if (invoice.getNetAmount() == null || invoice.getVatAmount() == null || invoice.getGrossAmount() == null) {
            return false;
        }
        BigDecimal expectedGross = invoice.getNetAmount().add(invoice.getVatAmount());
        return expectedGross.subtract(invoice.getGrossAmount()).abs().compareTo(VAT_TOLERANCE) > 0;
    }

    private String buildRiskSummary(List<String> flags, String riskLevel) {
        if (flags.isEmpty()) {
            return "未发现明显风险。";
        }
        String names = flags.stream().map(flag -> RISK_LABELS.getOrDefault(flag, flag)).reduce((a, b) -> a + "、" + b)
                .orElse("");
        String advice = FinanceInvoiceConstants.RISK_LEVEL_HIGH.equals(riskLevel) ? "建议加强审批并人工复核。" : "建议财务复核后提交审批。";
        return "发现 " + flags.size() + " 项风险：" + names + "，" + advice;
    }

    private String maxRiskLevel(String current, String candidate) {
        return riskWeight(candidate) > riskWeight(current) ? candidate : current;
    }

    private int riskWeight(String riskLevel) {
        return switch (riskLevel) {
            case FinanceInvoiceConstants.RISK_LEVEL_HIGH -> 3;
            case FinanceInvoiceConstants.RISK_LEVEL_MEDIUM -> 2;
            case FinanceInvoiceConstants.RISK_LEVEL_LOW -> 1;
            default -> 0;
        };
    }

    private static Map<String, String> buildRiskLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(FinanceInvoiceConstants.RISK_FLAG_NEW_SUPPLIER, "新供应商");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_IBAN_CHANGED, "IBAN 变化");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_NO_PO, "无 PO");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_NO_CONTRACT, "无合同");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_DUPLICATE_INVOICE, "重复发票");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_AMOUNT_OVER_LIMIT, "金额超限");
        labels.put(FinanceInvoiceConstants.RISK_FLAG_VAT_ABNORMAL, "VAT 异常");
        return labels;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

}
