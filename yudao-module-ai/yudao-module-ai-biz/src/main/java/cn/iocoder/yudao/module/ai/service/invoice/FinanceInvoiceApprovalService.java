package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceSubmitApprovalReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;

/**
 * Finance invoice approval adapter.
 */
public interface FinanceInvoiceApprovalService {

    String submitApproval(FinanceInvoiceDO invoice, FinanceInvoiceSubmitApprovalReqVO reqVO);

    void syncApprovalResult(String processInstanceId, String approvalStatus);

}
