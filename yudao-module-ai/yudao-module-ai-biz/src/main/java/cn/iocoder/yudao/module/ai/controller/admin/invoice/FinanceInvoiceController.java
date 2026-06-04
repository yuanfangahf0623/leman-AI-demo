package cn.iocoder.yudao.module.ai.controller.admin.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceApprovalResultReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceConfirmReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoicePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceRecognizeReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceRiskRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceSubmitApprovalReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdateBookkeepingReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoiceUpdatePaymentReqVO;
import cn.iocoder.yudao.module.ai.convert.FinanceInvoiceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import cn.iocoder.yudao.module.ai.service.invoice.FinanceInvoiceApprovalService;
import cn.iocoder.yudao.module.ai.service.invoice.FinanceInvoicePreview;
import cn.iocoder.yudao.module.ai.service.invoice.FinanceInvoiceService;
import cn.iocoder.yudao.module.ai.service.invoice.InvoiceRiskResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Finance invoice admin Controller.
 */
@RestController
@RequestMapping("/admin-api/finance/invoice")
@Validated
@RequiredArgsConstructor
public class FinanceInvoiceController {

    private final FinanceInvoiceService invoiceService;
    private final FinanceInvoiceApprovalService invoiceApprovalService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('finance:invoice:create')")
    public CommonResult<Long> uploadInvoice(@RequestParam("file") MultipartFile file) {
        return CommonResult.success(invoiceService.uploadInvoice(file));
    }

    @PostMapping("/{id}/recognize")
    @PreAuthorize("@ss.hasPermission('finance:invoice:recognize')")
    public CommonResult<Boolean> recognizeInvoice(@PathVariable("id") @NotNull(message = "invoice id is required") Long id,
                                                  @Valid @RequestBody(required = false) FinanceInvoiceRecognizeReqVO reqVO) {
        boolean force = reqVO != null && Boolean.TRUE.equals(reqVO.getForce());
        invoiceService.recognizeInvoice(id, force);
        return CommonResult.success(true);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('finance:invoice:query')")
    public CommonResult<FinanceInvoiceRespVO> getInvoice(@PathVariable("id") @NotNull(message = "invoice id is required") Long id) {
        FinanceInvoiceRespVO respVO = FinanceInvoiceConvert.INSTANCE.convert(invoiceService.getInvoice(id));
        respVO.setAttachments(FinanceInvoiceConvert.INSTANCE.convertAttachmentList(invoiceService.getInvoiceAttachments(id)));
        return CommonResult.success(respVO);
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('finance:invoice:query')")
    public CommonResult<FinanceInvoiceRespVO> getInvoiceByParam(@RequestParam("id") @NotNull(message = "invoice id is required") Long id) {
        return getInvoice(id);
    }

    @GetMapping("/preview/{id}")
    @PreAuthorize("@ss.hasPermission('finance:invoice:query')")
    public ResponseEntity<InputStreamResource> previewInvoice(@PathVariable("id") @NotNull(message = "invoice id is required") Long id) {
        FinanceInvoicePreview preview = invoiceService.getInvoicePreview(id);
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(preview.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(preview.getInputStream()));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermission('finance:invoice:confirm')")
    public CommonResult<FinanceInvoiceRiskRespVO> confirmInvoice(@PathVariable("id") @NotNull(message = "invoice id is required") Long id,
                                                                 @Valid @RequestBody FinanceInvoiceConfirmReqVO reqVO) {
        InvoiceRiskResult riskResult = invoiceService.confirmInvoice(id, reqVO);
        FinanceInvoiceRiskRespVO respVO = new FinanceInvoiceRiskRespVO();
        respVO.setRiskLevel(riskResult.getRiskLevel());
        respVO.setRiskFlags(riskResult.getRiskFlags());
        respVO.setRiskSummary(riskResult.getRiskSummary());
        return CommonResult.success(respVO);
    }

    @PostMapping("/{id}/submit-approval")
    @PreAuthorize("@ss.hasPermission('finance:invoice:submit')")
    public CommonResult<String> submitApproval(@PathVariable("id") @NotNull(message = "invoice id is required") Long id,
                                               @Valid @RequestBody(required = false) FinanceInvoiceSubmitApprovalReqVO reqVO) {
        return CommonResult.success(invoiceService.submitApproval(id, reqVO));
    }

    @PostMapping("/approval-result")
    @PreAuthorize("@ss.hasPermission('finance:invoice:submit')")
    public CommonResult<Boolean> syncApprovalResult(@Valid @RequestBody FinanceInvoiceApprovalResultReqVO reqVO) {
        invoiceApprovalService.syncApprovalResult(reqVO.getProcessInstanceId(), reqVO.getApprovalStatus());
        return CommonResult.success(true);
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('finance:invoice:query')")
    public CommonResult<PageResult<FinanceInvoiceRespVO>> getInvoicePage(@Valid FinanceInvoicePageReqVO pageReqVO) {
        PageResult<FinanceInvoiceDO> pageResult = invoiceService.getInvoicePage(pageReqVO);
        return CommonResult.success(FinanceInvoiceConvert.INSTANCE.convertPage(pageResult));
    }

    @PutMapping("/{id}/bookkeeping")
    @PreAuthorize("@ss.hasPermission('finance:invoice:book-update')")
    public CommonResult<Boolean> updateBookkeeping(@PathVariable("id") @NotNull(message = "invoice id is required") Long id,
                                                   @Valid @RequestBody FinanceInvoiceUpdateBookkeepingReqVO reqVO) {
        invoiceService.updateBookkeeping(id, reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/payment")
    @PreAuthorize("@ss.hasPermission('finance:invoice:payment-update')")
    public CommonResult<Boolean> updatePayment(@PathVariable("id") @NotNull(message = "invoice id is required") Long id,
                                               @Valid @RequestBody FinanceInvoiceUpdatePaymentReqVO reqVO) {
        invoiceService.updatePayment(id, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('finance:invoice:delete')")
    public CommonResult<Boolean> deleteInvoice(@PathVariable("id") @NotNull(message = "invoice id is required") Long id) {
        invoiceService.deleteInvoice(id);
        return CommonResult.success(true);
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('finance:invoice:export')")
    public ResponseEntity<byte[]> exportInvoices(@Valid FinanceInvoicePageReqVO pageReqVO) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("finance-invoice.xlsx").build().toString())
                .body(invoiceService.exportInvoices(pageReqVO));
    }

}
