package cn.iocoder.yudao.module.ai.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiDataSourceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
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
 * AI data source admin controller.
 */
@RestController
@RequestMapping("/admin-api/ai/datasource")
@Validated
@RequiredArgsConstructor
public class AiDataSourceController {

    private final AiDataSourceService dataSourceService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<PageResult<AiDataSourceRespVO>> getDataSourcePage(@Valid AiDataSourcePageReqVO pageReqVO) {
        PageResult<AiDataSourceDO> pageResult = dataSourceService.getDataSourcePage(pageReqVO);
        return CommonResult.success(AiDataSourceConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<AiDataSourceRespVO> getDataSource(@RequestParam("id") @NotNull(message = "数据源编号不能为空") Long id) {
        return CommonResult.success(AiDataSourceConvert.INSTANCE.convert(dataSourceService.getDataSource(id)));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('ai:datasource:create')")
    public CommonResult<Long> createDataSource(@Valid @RequestBody AiDataSourceCreateReqVO createReqVO) {
        return CommonResult.success(dataSourceService.createDataSource(createReqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('ai:datasource:update')")
    public CommonResult<Boolean> updateDataSource(@Valid @RequestBody AiDataSourceUpdateReqVO updateReqVO) {
        dataSourceService.updateDataSource(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('ai:datasource:delete')")
    public CommonResult<Boolean> deleteDataSource(@RequestParam("id") @NotNull(message = "数据源编号不能为空") Long id) {
        dataSourceService.deleteDataSource(id);
        return CommonResult.success(true);
    }

}
