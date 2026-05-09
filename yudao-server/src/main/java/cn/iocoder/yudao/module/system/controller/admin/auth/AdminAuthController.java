package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台认证 Controller。
 */
@RestController
@RequestMapping("/admin-api/system/auth")
@Validated
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;

    @PostMapping("/login")
    public CommonResult<AuthLoginRespVO> login(@Valid @RequestBody AuthLoginReqVO reqVO, HttpServletRequest request) {
        Long tenantId = parseTenantId(request.getHeader("tenant-id"));
        return CommonResult.success(authService.login(reqVO, resolveClientIp(request), tenantId));
    }

    @PostMapping("/refresh-token")
    public CommonResult<AuthLoginRespVO> refreshToken(
            @RequestParam("refreshToken") @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
        return CommonResult.success(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public CommonResult<Boolean> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                        String authorization) {
        authService.logout(authorization);
        return CommonResult.success(true);
    }

    @GetMapping("/get-permission-info")
    public CommonResult<AuthPermissionInfoRespVO> getPermissionInfo() {
        return CommonResult.success(authService.getPermissionInfo());
    }

    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
