package cn.iocoder.yudao.server.framework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具。
 */
public final class SecurityFrameworkUtils {

    private SecurityFrameworkUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    public static Long getLoginUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

    public static Long getLoginTenantId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getTenantId();
    }

}
