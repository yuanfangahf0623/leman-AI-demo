package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceSubmitApprovalReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.dal.mysql.FinanceInvoiceMapper;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.FinanceInvoiceErrorCodeConstants.INVOICE_APPROVAL_START_FAILED;

/**
 * Finance invoice approval adapter implementation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceInvoiceApprovalServiceImpl implements FinanceInvoiceApprovalService {

    private final FinanceInvoiceMapper invoiceMapper;
    private final AiProperties aiProperties;

    @Override
    public String submitApproval(FinanceInvoiceDO invoice, FinanceInvoiceSubmitApprovalReqVO reqVO) {
        if (!Boolean.TRUE.equals(aiProperties.getInvoice().getMockApprovalEnabled())) {
            throw new ServiceException(INVOICE_APPROVAL_START_FAILED,
                    "BPM process engine is not wired in this repository");
        }
        Map<String, Object> variables = buildProcessVariables(invoice);
        String processInstanceId = "mock-finance-invoice-" + invoice.getId() + "-"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        log.info("Mock start finance invoice approval, invoiceId={}, tenantId={}, processDefinitionKey={}, variables={}",
                invoice.getId(), invoice.getTenantId(), invoice.getProcessDefinitionKey(), variables);
        return processInstanceId;
    }

    @Override
    public void syncApprovalResult(String processInstanceId, String approvalStatus) {
        FinanceInvoiceDO invoice = invoiceMapper.selectByProcessInstanceIdAndTenantId(processInstanceId,
                AiUserContextHolder.getTenantId());
        if (invoice == null) {
            return;
        }
        FinanceInvoiceDO updateObj = new FinanceInvoiceDO();
        updateObj.setId(invoice.getId());
        updateObj.setApprovalStatus(approvalStatus);
        if (FinanceInvoiceConstants.APPROVAL_STATUS_APPROVED.equals(approvalStatus)) {
            updateObj.setBookkeepingStatus(FinanceInvoiceConstants.BOOKKEEPING_STATUS_NOT_BOOKED);
            updateObj.setPaymentStatus(FinanceInvoiceConstants.PAYMENT_STATUS_NOT_PAID);
        }
        invoiceMapper.updateByIdAndTenantId(updateObj, invoice.getTenantId());
    }

    private Map<String, Object> buildProcessVariables(FinanceInvoiceDO invoice) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("invoiceId", invoice.getId());
        variables.put("invoiceCode", invoice.getInvoiceCode());
        variables.put("supplierName", invoice.getSupplierName());
        variables.put("invoiceNo", invoice.getInvoiceNo());
        variables.put("currency", invoice.getCurrency());
        variables.put("grossAmount", invoice.getGrossAmount());
        variables.put("expenseCategory", invoice.getExpenseCategory());
        variables.put("riskLevel", invoice.getRiskLevel());
        variables.put("riskFlags", invoice.getRiskFlags());
        variables.put("hasIbanChanged", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_IBAN_CHANGED));
        variables.put("isNewSupplier", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_NEW_SUPPLIER));
        variables.put("noPo", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_NO_PO));
        variables.put("noContract", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_NO_CONTRACT));
        variables.put("amountOverLimit", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_AMOUNT_OVER_LIMIT));
        variables.put("duplicateInvoice", hasRiskFlag(invoice, FinanceInvoiceConstants.RISK_FLAG_DUPLICATE_INVOICE));
        variables.put("submitUserId", AiUserContextHolder.getUserId());
        return variables;
    }

    private boolean hasRiskFlag(FinanceInvoiceDO invoice, String riskFlag) {
        return invoice.getRiskFlags() != null && invoice.getRiskFlags().contains("\"" + riskFlag + "\"");
    }

}
