package cn.iocoder.yudao.module.system.controller.admin.basic;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.service.user.SharedHrService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin-api/shared-hr")
@RequiredArgsConstructor
@Validated
public class SharedHrController {
    private final SharedHrService service;
    @GetMapping("/organizations")
    public CommonResult<List<Map<String,Object>>> organizations() { return CommonResult.success(service.organizations()); }
    @GetMapping("/people")
    public CommonResult<List<Map<String,Object>>> people(
            @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue="100") @Min(1) @Max(500) int size) {
        return CommonResult.success(service.people(page,size));
    }
    @GetMapping("/status")
    public CommonResult<Map<String,Object>> status() { return CommonResult.success(service.status()); }
}
