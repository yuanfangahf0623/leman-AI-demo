package cn.iocoder.yudao.module.ai.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatRespVO;
import cn.iocoder.yudao.module.ai.convert.AiDataSourceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceRawRecordService;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrAttendanceStatService;
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
 * AI 数据源管理端 Controller。
 *
 * <p>Controller 保持轻量，只负责请求入口、权限和统一响应，数据源归属和合法性校验放在 Service。</p>
 */
@RestController
@RequestMapping("/admin-api/ai/datasource")
@Validated
@RequiredArgsConstructor
public class AiDataSourceController {

    private final AiDataSourceService dataSourceService;
    private final AiDataSourceRawRecordService rawRecordService;
    private final TwoHaoHrAttendanceStatService twoHaoHrAttendanceStatService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<PageResult<AiDataSourceRespVO>> getDataSourcePage(@Valid AiDataSourcePageReqVO pageReqVO) {
        // 支持按 knowledgeBaseId 筛选数据源，实际租户过滤由 Service/Mapper 完成。
        PageResult<AiDataSourceDO> pageResult = dataSourceService.getDataSourcePage(pageReqVO);
        return CommonResult.success(AiDataSourceConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<AiDataSourceRespVO> getDataSource(@RequestParam("id") @NotNull(message = "数据源编号不能为空") Long id) {
        // 详情返回响应 VO，不直接暴露 DO。
        return CommonResult.success(AiDataSourceConvert.INSTANCE.convert(dataSourceService.getDataSource(id)));
    }

    @GetMapping("/raw-record/page")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<PageResult<AiDataSourceRawRecordRespVO>> getRawRecordPage(
            @Valid AiDataSourceRawRecordPageReqVO pageReqVO) {
        return CommonResult.success(rawRecordService.getRawRecordPage(pageReqVO));
    }

    @GetMapping("/twohaohr/attendance/department-stat")
    @PreAuthorize("@ss.hasPermission('ai:datasource:query')")
    public CommonResult<TwoHaoHrAttendanceStatRespVO> getTwoHaoHrAttendanceDepartmentStat(
            @Valid TwoHaoHrAttendanceStatReqVO reqVO) {
        return CommonResult.success(twoHaoHrAttendanceStatService.getDepartmentStat(reqVO));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('ai:datasource:create')")
    public CommonResult<Long> createDataSource(@Valid @RequestBody AiDataSourceCreateReqVO createReqVO) {
        // 创建前会在 Service 校验 knowledgeBaseId 是否存在且属于当前租户。
        return CommonResult.success(dataSourceService.createDataSource(createReqVO));
    }

    @PostMapping("/ingest")
    @PreAuthorize("@ss.hasPermission('ai:datasource:update')")
    public CommonResult<AiDataSourceIngestRespVO> ingest(@Valid @RequestBody AiDataSourceIngestReqVO ingestReqVO) {
        return CommonResult.success(dataSourceService.ingest(ingestReqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('ai:datasource:update')")
    public CommonResult<Boolean> updateDataSource(@Valid @RequestBody AiDataSourceUpdateReqVO updateReqVO) {
        // 更新时不解析 configJson，先按字符串持久化，复杂连接校验后续补充。
        dataSourceService.updateDataSource(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('ai:datasource:delete')")
    public CommonResult<Boolean> deleteDataSource(@RequestParam("id") @NotNull(message = "数据源编号不能为空") Long id) {
        // 删除数据源不清理文档，后续单独提供文档清理能力。
        dataSourceService.deleteDataSource(id);
        return CommonResult.success(true);
    }

}
