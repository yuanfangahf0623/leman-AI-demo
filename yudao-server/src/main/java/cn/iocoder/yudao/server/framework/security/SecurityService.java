package cn.iocoder.yudao.server.framework.security;

import org.springframework.stereotype.Component;

/**
 * 兼容 yudao Controller 中的 @PreAuthorize("@ss.hasPermission(...)") 表达式。
 */
@Component("ss")
public class SecurityService {

    public boolean hasPermission(String permission) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return false;
        }
        return loginUser.isAdmin() || loginUser.getPermissions().contains("*:*:*")
                || loginUser.getPermissions().contains(permission);
    }

}
