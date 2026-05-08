package cn.iocoder.yudao.module.ai.controller.admin.document;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentRespVO;
import cn.iocoder.yudao.module.ai.convert.AiDocumentConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 文档管理端 Controller。
 *
 * <p>文件上传是高风险入口，Controller 只接收 multipart 请求，安全校验和存储编排统一交给 Service。</p>
 */
@RestController
@RequestMapping("/admin-api/ai/document")
@Validated
@RequiredArgsConstructor
public class AiDocumentController {

    private final AiDocumentService documentService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('ai:document:query')")
    public CommonResult<PageResult<AiDocumentRespVO>> getDocumentPage(@Valid AiDocumentPageReqVO pageReqVO) {
        // 支持按知识库、解析状态、向量化状态和标题查询，具体租户过滤由 Service/Mapper 处理。
        PageResult<AiDocumentDO> pageResult = documentService.getDocumentPage(pageReqVO);
        return CommonResult.success(AiDocumentConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('ai:document:query')")
    public CommonResult<AiDocumentRespVO> getDocument(@RequestParam("id") @NotNull(message = "文档编号不能为空") Long id) {
        // 详情查询会在 Service 校验当前用户对文档所属知识库的访问权限。
        return CommonResult.success(AiDocumentConvert.INSTANCE.convert(documentService.getDocument(id)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('ai:document:upload')")
    public CommonResult<Long> uploadDocument(@RequestParam("knowledgeBaseId")
                                             @NotNull(message = "知识库编号不能为空") Long knowledgeBaseId,
                                             @RequestParam("file") MultipartFile file) {
        // 上传后只返回 documentId，不在 HTTP 请求中同步解析或向量化。
        return CommonResult.success(documentService.uploadDocument(knowledgeBaseId, file));
    }

    @PostMapping("/parse")
    @PreAuthorize("@ss.hasPermission('ai:document:parse')")
    public CommonResult<Boolean> parseDocument(@RequestParam("id") @NotNull(message = "文档编号不能为空") Long id) {
        // 第一阶段同步执行解析和切片，后续可在 Service 内改成投递 MQ 异步任务。
        documentService.parseDocument(id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('ai:document:delete')")
    public CommonResult<Boolean> deleteDocument(@RequestParam("id") @NotNull(message = "文档编号不能为空") Long id) {
        // 删除文档时会同步逻辑删除 chunk，暂不删除向量库数据。
        documentService.deleteDocument(id);
        return CommonResult.success(true);
    }

}
