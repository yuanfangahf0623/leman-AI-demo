package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceConfirmReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceRespVO.AttachmentRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.service.invoice.InvoiceAiResult;

import java.util.List;

/**
 * Finance invoice Convert.
 */
public class FinanceInvoiceConvert {

    public static final FinanceInvoiceConvert INSTANCE = new FinanceInvoiceConvert();

    public FinanceInvoiceRespVO convert(FinanceInvoiceDO bean) {
        if (bean == null) {
            return null;
        }
        FinanceInvoiceRespVO result = new FinanceInvoiceRespVO();
        result.setId(bean.getId());
        result.setInvoiceCode(bean.getInvoiceCode());
        result.setSourceType(bean.getSourceType());
        result.setSourceMessageId(bean.getSourceMessageId());
        result.setSourceSender(bean.getSourceSender());
        result.setSourceReceivedTime(bean.getSourceReceivedTime());
        result.setFileId(bean.getFileId());
        result.setFileUrl(bean.getFileUrl());
        result.setFileName(bean.getFileName());
        result.setFileType(bean.getFileType());
        result.setFileHash(bean.getFileHash());
        result.setFileSize(bean.getFileSize());
        result.setSupplierName(bean.getSupplierName());
        result.setSupplierTaxNo(bean.getSupplierTaxNo());
        result.setInvoiceNo(bean.getInvoiceNo());
        result.setInvoiceDate(bean.getInvoiceDate());
        result.setDueDate(bean.getDueDate());
        result.setCurrency(bean.getCurrency());
        result.setNetAmount(bean.getNetAmount());
        result.setVatAmount(bean.getVatAmount());
        result.setGrossAmount(bean.getGrossAmount());
        result.setIban(bean.getIban());
        result.setBic(bean.getBic());
        result.setPaymentAccountName(bean.getPaymentAccountName());
        result.setExpenseCategory(bean.getExpenseCategory());
        result.setBusinessDesc(bean.getBusinessDesc());
        result.setPoNo(bean.getPoNo());
        result.setContractNo(bean.getContractNo());
        result.setProjectName(bean.getProjectName());
        result.setAiStatus(bean.getAiStatus());
        result.setAiConfidence(bean.getAiConfidence());
        result.setAiRawResult(bean.getAiRawResult());
        result.setAiSummary(bean.getAiSummary());
        result.setAiErrorMessage(bean.getAiErrorMessage());
        result.setFinanceReviewStatus(bean.getFinanceReviewStatus());
        result.setRiskLevel(bean.getRiskLevel());
        result.setRiskFlags(bean.getRiskFlags());
        result.setRiskSummary(bean.getRiskSummary());
        result.setApprovalStatus(bean.getApprovalStatus());
        result.setProcessInstanceId(bean.getProcessInstanceId());
        result.setProcessDefinitionKey(bean.getProcessDefinitionKey());
        result.setBookkeepingStatus(bean.getBookkeepingStatus());
        result.setPaymentStatus(bean.getPaymentStatus());
        result.setPaymentTime(bean.getPaymentTime());
        result.setPaymentRemark(bean.getPaymentRemark());
        result.setRemark(bean.getRemark());
        result.setCreateTime(bean.getCreateTime());
        result.setUpdateTime(bean.getUpdateTime());
        return result;
    }

    public PageResult<FinanceInvoiceRespVO> convertPage(PageResult<FinanceInvoiceDO> page) {
        if (page == null) {
            return null;
        }
        return new PageResult<>(page.getList().stream().map(this::convert).toList(), page.getTotal());
    }

    public FinanceInvoiceDO convertConfirm(FinanceInvoiceConfirmReqVO bean) {
        if (bean == null) {
            return null;
        }
        FinanceInvoiceDO result = new FinanceInvoiceDO();
        result.setSupplierName(bean.getSupplierName());
        result.setSupplierTaxNo(bean.getSupplierTaxNo());
        result.setInvoiceNo(bean.getInvoiceNo());
        result.setInvoiceDate(bean.getInvoiceDate());
        result.setDueDate(bean.getDueDate());
        result.setCurrency(bean.getCurrency());
        result.setNetAmount(bean.getNetAmount());
        result.setVatAmount(bean.getVatAmount());
        result.setGrossAmount(bean.getGrossAmount());
        result.setIban(bean.getIban());
        result.setBic(bean.getBic());
        result.setPaymentAccountName(bean.getPaymentAccountName());
        result.setExpenseCategory(bean.getExpenseCategory());
        result.setBusinessDesc(bean.getBusinessDesc());
        result.setPoNo(bean.getPoNo());
        result.setContractNo(bean.getContractNo());
        result.setProjectName(bean.getProjectName());
        result.setRemark(bean.getRemark());
        return result;
    }

    public FinanceInvoiceDO convertAiResult(InvoiceAiResult bean) {
        if (bean == null) {
            return null;
        }
        FinanceInvoiceDO result = new FinanceInvoiceDO();
        result.setSupplierName(bean.getSupplierName());
        result.setSupplierTaxNo(bean.getSupplierTaxNo());
        result.setInvoiceNo(bean.getInvoiceNo());
        result.setInvoiceDate(bean.getInvoiceDate());
        result.setDueDate(bean.getDueDate());
        result.setCurrency(bean.getCurrency());
        result.setNetAmount(bean.getNetAmount());
        result.setVatAmount(bean.getVatAmount());
        result.setGrossAmount(bean.getGrossAmount());
        result.setIban(bean.getIban());
        result.setBic(bean.getBic());
        result.setPaymentAccountName(bean.getPaymentAccountName());
        result.setExpenseCategory(bean.getExpenseCategory());
        result.setBusinessDesc(bean.getBusinessDesc());
        result.setPoNo(bean.getPoNo());
        result.setContractNo(bean.getContractNo());
        result.setProjectName(bean.getProjectName());
        result.setAiConfidence(bean.getConfidence());
        result.setAiSummary(bean.getSummary());
        result.setAiRawResult(bean.getRawJson());
        result.setAiErrorMessage(bean.getErrorMessage());
        return result;
    }

    public List<AttachmentRespVO> convertAttachmentList(List<FinanceInvoiceAttachmentDO> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream().map(this::convertAttachment).toList();
    }

    private AttachmentRespVO convertAttachment(FinanceInvoiceAttachmentDO bean) {
        AttachmentRespVO result = new AttachmentRespVO();
        result.setId(bean.getId());
        result.setFileName(bean.getFileName());
        result.setFileUrl(bean.getFileUrl());
        result.setFileType(bean.getFileType());
        result.setFileSize(bean.getFileSize());
        result.setFileHash(bean.getFileHash());
        result.setAttachmentType(bean.getAttachmentType());
        result.setCreateTime(bean.getCreateTime());
        return result;
    }

}
