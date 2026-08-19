package cn.iocoder.yudao.module.dataplatform.controller.admin.sync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.JobRunPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;
import cn.iocoder.yudao.module.dataplatform.service.sync.DataPlatformSyncJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin-api/data-platform/sync-job")
@Validated
@RequiredArgsConstructor
public class DataPlatformSyncJobController {

    private final DataPlatformSyncJobService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:query')")
    public CommonResult<PageResult<DataPlatformSyncJobDO>> page(@Valid SyncJobPageReqVO reqVO) {
        return CommonResult.success(service.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:query')")
    public CommonResult<DataPlatformSyncJobDO> get(@RequestParam("id") @NotNull Long id) {
        return CommonResult.success(service.get(id));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:create')")
    public CommonResult<Long> create(@Valid @RequestBody SyncJobSaveReqVO reqVO) {
        return CommonResult.success(service.create(reqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody SyncJobSaveReqVO reqVO) {
        service.update(reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") @NotNull Long id) {
        service.delete(id);
        return CommonResult.success(true);
    }

    @PostMapping("/execute")
    @PreAuthorize("@ss.hasPermission('data-platform:sync-job:execute')")
    public CommonResult<Long> execute(@RequestParam("id") @NotNull Long id) {
        return CommonResult.success(service.execute(id, SecurityContextHolder.getContext().getAuthentication().getName()));
    }

    @GetMapping("/run/page")
    @PreAuthorize("@ss.hasPermission('data-platform:job-log:query')")
    public CommonResult<PageResult<DataPlatformJobRunDO>> runPage(@Valid JobRunPageReqVO reqVO) {
        return CommonResult.success(service.runPage(reqVO));
    }

    @GetMapping("/run/log")
    @PreAuthorize("@ss.hasPermission('data-platform:job-log:query')")
    public CommonResult<String> runLog(@RequestParam("id") @NotNull Long id,
                                       @RequestParam(value = "tailLines", defaultValue = "300")
                                       @Min(1) @Max(1000) int tailLines) {
        return CommonResult.success(service.readRunLog(id, tailLines));
    }
}
