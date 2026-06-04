package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceConfirmReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoicePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceSubmitApprovalReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdateBookkeepingReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdatePaymentReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceAttachmentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Finance invoice service.
 */
public interface FinanceInvoiceService {

    Long uploadInvoice(MultipartFile file);

    Long createInvoiceFromSource(FinanceInvoiceSourceFile sourceFile);

    FinanceInvoiceDO getInvoice(Long id);

    List<FinanceInvoiceAttachmentDO> getInvoiceAttachments(Long invoiceId);

    FinanceInvoicePreview getInvoicePreview(Long id);

    PageResult<FinanceInvoiceDO> getInvoicePage(FinanceInvoicePageReqVO pageReqVO);

    FinanceInvoiceDO recognizeInvoice(Long id, boolean force);

    InvoiceRiskResult confirmInvoice(Long id, FinanceInvoiceConfirmReqVO reqVO);

    String submitApproval(Long id, FinanceInvoiceSubmitApprovalReqVO reqVO);

    void updatePayment(Long id, FinanceInvoiceUpdatePaymentReqVO reqVO);

    void updateBookkeeping(Long id, FinanceInvoiceUpdateBookkeepingReqVO reqVO);

    void deleteInvoice(Long id);

    byte[] exportInvoices(FinanceInvoicePageReqVO pageReqVO);

}
