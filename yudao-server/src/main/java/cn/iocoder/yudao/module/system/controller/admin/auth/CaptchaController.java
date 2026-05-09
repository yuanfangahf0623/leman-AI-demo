package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地联调验证码接口。当前前端默认关闭验证码，保留兼容端点。
 */
@RestController
@RequestMapping("/admin-api/system/captcha")
public class CaptchaController {

    @PostMapping("/get")
    public CommonResult<Map<String, Object>> get() {
        return CommonResult.success(Map.of("captchaVerification", "local-dev"));
    }

    @PostMapping("/check")
    public CommonResult<Boolean> check() {
        return CommonResult.success(true);
    }

}
