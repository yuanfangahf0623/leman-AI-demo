package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录前租户查询兼容接口。
 */
@RestController
@RequestMapping("/admin-api/system/tenant")
public class TenantPublicController {

    @GetMapping("/get-id-by-name")
    public CommonResult<Long> getTenantIdByName(@RequestParam("name") String name) {
        return CommonResult.success(1L);
    }

    @GetMapping("/get-by-website")
    public CommonResult<Map<String, Object>> getTenantByWebsite(@RequestParam("website") String website) {
        return CommonResult.success(Map.of("id", 1L, "name", "默认租户"));
    }

}
