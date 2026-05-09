package cn.iocoder.yudao.module.ai.controller.admin.sync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;
import cn.iocoder.yudao.module.ai.service.sync.AiSyncJobService;
import cn.iocoder.yudao.module.ai.service.sync.KnowledgeSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 同步任务管理端 Controller。
 *
 * <p>Controller 只负责创建入口、权限和参数校验，任务状态初始化与归属校验交给 Service。</p>
 */
@RestController
@RequestMapping("/admin-api/ai/sync/job")
@Validated
@RequiredArgsConstructor
public class AiSyncJobController {

    private final AiSyncJobService syncJobService;
    private final KnowledgeSyncService knowledgeSyncService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('ai:sync-job:create')")
    public CommonResult<Long> createSyncJob(@Valid @RequestBody AiSyncJobCreateReqVO createReqVO) {
        // 第一阶段只创建 PENDING 任务，不在 HTTP 请求中执行真实同步。
        return CommonResult.success(syncJobService.createSyncJob(createReqVO));
    }

    @PostMapping("/execute")
    @PreAuthorize("@ss.hasPermission('ai:sync-job:execute')")
    public CommonResult<Boolean> executeSyncJob(@RequestParam("id") @NotNull(message = "同步任务编号不能为空") Long id) {
        // 第一阶段同步执行 FILE 数据源，后续可替换为 MQ 异步调度。
        knowledgeSyncService.executeSyncJob(id);
        return CommonResult.success(true);
    }

}
