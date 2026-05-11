package cn.iocoder.yudao.module.ai.controller.admin.knowledge;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryListReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiKnowledgeDirectoryConvert;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeDirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 知识库目录管理端 Controller。
 */
@RestController
@RequestMapping("/admin-api/ai/knowledge-directory")
@Validated
@RequiredArgsConstructor
public class AiKnowledgeDirectoryController {

    private final AiKnowledgeDirectoryService directoryService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<List<AiKnowledgeDirectoryRespVO>> getDirectoryList(
            @Valid AiKnowledgeDirectoryListReqVO listReqVO) {
        return CommonResult.success(AiKnowledgeDirectoryConvert.INSTANCE.convertList(
                directoryService.getDirectoryList(listReqVO)));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<AiKnowledgeDirectoryRespVO> getDirectory(
            @RequestParam("id") @NotNull(message = "目录编号不能为空") Long id) {
        return CommonResult.success(AiKnowledgeDirectoryConvert.INSTANCE.convert(directoryService.getDirectory(id)));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:update')")
    public CommonResult<Long> createDirectory(@Valid @RequestBody AiKnowledgeDirectoryCreateReqVO createReqVO) {
        return CommonResult.success(directoryService.createDirectory(createReqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:update')")
    public CommonResult<Boolean> updateDirectory(@Valid @RequestBody AiKnowledgeDirectoryUpdateReqVO updateReqVO) {
        directoryService.updateDirectory(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:update')")
    public CommonResult<Boolean> deleteDirectory(
            @RequestParam("id") @NotNull(message = "目录编号不能为空") Long id) {
        directoryService.deleteDirectory(id);
        return CommonResult.success(true);
    }

}
