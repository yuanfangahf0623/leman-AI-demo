package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin-api/data-platform/datasource")
@Validated
@RequiredArgsConstructor
public class DataPlatformDataSourceController {

    private final DataPlatformDataSourceService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:query')")
    public CommonResult<PageResult<DataSourceRespVO>> page(@Valid DataSourcePageReqVO reqVO) {
        return CommonResult.success(service.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:query')")
    public CommonResult<DataSourceRespVO> get(@RequestParam("id") @NotNull Long id) {
        return CommonResult.success(service.get(id));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:create')")
    public CommonResult<Long> create(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        return CommonResult.success(service.create(reqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        service.update(reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") @NotNull Long id) {
        service.delete(id);
        return CommonResult.success(true);
    }

    @PostMapping("/test")
    @PreAuthorize("@ss.hasPermission('data-platform:datasource:query')")
    public CommonResult<Boolean> test(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        service.testConnection(reqVO);
        return CommonResult.success(true);
    }
}
