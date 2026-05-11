package cn.iocoder.yudao.module.ai.controller.admin.document;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiDocumentConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentPreview;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('ai:document:update')")
    public CommonResult<Boolean> updateDocument(@Valid @RequestBody AiDocumentUpdateReqVO updateReqVO) {
        // 只允许编辑文档元数据，不替换原始文件、不改写切片或向量数据。
        documentService.updateDocument(updateReqVO);
        return CommonResult.success(true);
    }

    @GetMapping("/preview")
    @PreAuthorize("@ss.hasPermission('ai:document:query')")
    public ResponseEntity<InputStreamResource> previewDocument(@RequestParam("id")
                                                               @NotNull(message = "文档编号不能为空") Long id) {
        AiDocumentPreview preview = documentService.getDocumentPreview(id);
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(preview.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(preview.getInputStream()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('ai:document:upload')")
    public CommonResult<Long> uploadDocument(@RequestParam("knowledgeBaseId")
                                             @NotNull(message = "知识库编号不能为空") Long knowledgeBaseId,
                                             @RequestParam(value = "directoryId", required = false) Long directoryId,
                                             @RequestParam("file") MultipartFile file) {
        // 上传后由 Service 自动尝试解析和向量化；失败时保留状态，前端可通过手动按钮重试。
        return CommonResult.success(documentService.uploadDocument(knowledgeBaseId, directoryId, file));
    }

    @PostMapping("/parse")
    @PreAuthorize("@ss.hasPermission('ai:document:parse')")
    public CommonResult<Boolean> parseDocument(@RequestParam("id") @NotNull(message = "文档编号不能为空") Long id) {
        // 第一阶段同步执行解析和切片，后续可在 Service 内改成投递 MQ 异步任务。
        documentService.parseDocument(id);
        return CommonResult.success(true);
    }

    @PostMapping("/embed")
    @PreAuthorize("@ss.hasPermission('ai:document:embed')")
    public CommonResult<Boolean> embedDocument(@RequestParam("id") @NotNull(message = "文档编号不能为空") Long id) {
        // 第一阶段同步执行向量化，后续可在 Service 内改成投递 MQ 异步任务。
        documentService.embedDocument(id);
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
