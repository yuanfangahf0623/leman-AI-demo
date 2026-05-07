package cn.iocoder.yudao.module.ai.controller.admin.knowledge;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiKnowledgeConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
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

/**
 * AI 知识库管理端 Controller。
 *
 * <p>Controller 只负责请求入口、权限校验、参数校验和统一响应封装，具体业务编排下沉到 Service。</p>
 */
@RestController
@RequestMapping("/admin-api/ai/knowledge")
@Validated
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final AiKnowledgeService knowledgeService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<PageResult<AiKnowledgeRespVO>> getKnowledgePage(@Valid AiKnowledgePageReqVO pageReqVO) {
        // 分页查询只透传查询条件，租户过滤由 Service/Mapper 层统一处理。
        PageResult<AiKnowledgeBaseDO> pageResult = knowledgeService.getKnowledgePage(pageReqVO);
        return CommonResult.success(AiKnowledgeConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:query')")
    public CommonResult<AiKnowledgeRespVO> getKnowledge(@RequestParam("id") @NotNull(message = "知识库编号不能为空") Long id) {
        // 详情查询不在 Controller 暴露底层 DO，统一转换为响应 VO。
        return CommonResult.success(AiKnowledgeConvert.INSTANCE.convert(knowledgeService.getKnowledge(id)));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:create')")
    public CommonResult<Long> createKnowledge(@Valid @RequestBody AiKnowledgeCreateReqVO createReqVO) {
        // 创建时的唯一性、默认值和租户隔离由 Service 负责。
        return CommonResult.success(knowledgeService.createKnowledge(createReqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:update')")
    public CommonResult<Boolean> updateKnowledge(@Valid @RequestBody AiKnowledgeUpdateReqVO updateReqVO) {
        // 更新入口只返回操作结果，避免在 Controller 编写业务判断。
        knowledgeService.updateKnowledge(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('ai:knowledge:delete')")
    public CommonResult<Boolean> deleteKnowledge(@RequestParam("id") @NotNull(message = "知识库编号不能为空") Long id) {
        // 删除使用逻辑删除，后续向量数据清理由独立任务处理。
        knowledgeService.deleteKnowledge(id);
        return CommonResult.success(true);
    }

}
