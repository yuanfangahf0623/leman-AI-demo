package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.*;
import cn.iocoder.yudao.module.dataplatform.service.metadata.DataPlatformMetadataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin-api/data-platform/data-dictionary")
@Validated
@RequiredArgsConstructor
public class DataPlatformMetadataController {

    private final DataPlatformMetadataService service;

    @GetMapping("/table/page")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:query')")
    public CommonResult<PageResult<MetadataTableRespVO>> pageTables(@Valid MetadataTablePageReqVO reqVO) {
        return CommonResult.success(service.pageTables(reqVO));
    }

    @GetMapping("/field/page")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:query')")
    public CommonResult<PageResult<MetadataFieldRespVO>> pageFields(@Valid MetadataFieldPageReqVO reqVO) {
        return CommonResult.success(service.pageFields(reqVO));
    }

    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:query')")
    public CommonResult<Map<String, Object>> summary(@RequestParam("dataSourceId") @NotNull Long dataSourceId) {
        return CommonResult.success(service.summary(dataSourceId));
    }

    @PostMapping("/refresh")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:refresh')")
    public CommonResult<MetadataRefreshRespVO> refresh(
            @RequestParam("dataSourceId") @NotNull Long dataSourceId) {
        return CommonResult.success(service.refresh(dataSourceId));
    }

    @PutMapping("/table/update")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:update')")
    public CommonResult<Boolean> updateTable(@Valid @RequestBody MetadataTableUpdateReqVO reqVO) {
        service.updateTable(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/field/update")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:update')")
    public CommonResult<Boolean> updateField(@Valid @RequestBody MetadataFieldUpdateReqVO reqVO) {
        service.updateField(reqVO);
        return CommonResult.success(true);
    }
}
