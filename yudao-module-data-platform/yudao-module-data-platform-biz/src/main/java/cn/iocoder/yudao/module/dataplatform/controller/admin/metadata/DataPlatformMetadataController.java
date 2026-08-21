package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.*;
import cn.iocoder.yudao.module.dataplatform.service.metadata.DataPlatformMetadataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/export-review")
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:query')")
    public void exportReview(@RequestParam("dataSourceId") @NotNull Long dataSourceId,
                             HttpServletResponse response) throws Exception {
        String filename = URLEncoder.encode("字段数据字典审核.tsv", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("text/tab-separated-values;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename);
        service.exportReviewTsv(dataSourceId, response.getOutputStream());
    }

    @PostMapping(value = "/import-review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('data-platform:data-dictionary:update')")
    public CommonResult<MetadataImportRespVO> importReview(
            @RequestParam("dataSourceId") @NotNull Long dataSourceId,
            @RequestPart("file") MultipartFile file) {
        return CommonResult.success(service.importReviewTsv(dataSourceId, file));
    }
}
