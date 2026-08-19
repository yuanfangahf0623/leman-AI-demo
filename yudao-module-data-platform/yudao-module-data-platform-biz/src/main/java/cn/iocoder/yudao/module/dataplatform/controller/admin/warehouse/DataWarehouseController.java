package cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseColumnRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseTableRespVO;
import cn.iocoder.yudao.module.dataplatform.service.warehouse.DataWarehouseService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin-api/data-platform/warehouse")
@Validated
@RequiredArgsConstructor
public class DataWarehouseController {

    private final DataWarehouseService service;

    @GetMapping("/health")
    @PreAuthorize("@ss.hasPermission('data-platform:warehouse:query')")
    public CommonResult<Map<String, Object>> health() {
        return CommonResult.success(service.health());
    }

    @GetMapping("/database/list")
    @PreAuthorize("@ss.hasPermission('data-platform:warehouse:query')")
    public CommonResult<List<String>> databases() {
        return CommonResult.success(service.listDatabases());
    }

    @GetMapping("/table/list")
    @PreAuthorize("@ss.hasPermission('data-platform:warehouse:query')")
    public CommonResult<List<WarehouseTableRespVO>> tables(@RequestParam("database") @NotBlank String database) {
        return CommonResult.success(service.listTables(database));
    }

    @GetMapping("/column/list")
    @PreAuthorize("@ss.hasPermission('data-platform:warehouse:query')")
    public CommonResult<List<WarehouseColumnRespVO>> columns(
            @RequestParam("database") @NotBlank String database,
            @RequestParam("table") @NotBlank String table) {
        return CommonResult.success(service.listColumns(database, table));
    }
}
