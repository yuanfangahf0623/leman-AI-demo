package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO.UserInfo;
import cn.iocoder.yudao.module.system.dal.dataobject.SystemUserDO;
import cn.iocoder.yudao.module.system.service.menu.SystemMenuService;
import cn.iocoder.yudao.module.system.service.user.SystemUserService;
import cn.iocoder.yudao.server.framework.security.LoginUser;
import cn.iocoder.yudao.server.framework.security.SecurityFrameworkUtils;
import cn.iocoder.yudao.server.framework.security.TokenSession;
import cn.iocoder.yudao.server.framework.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 管理后台认证 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final Integer USER_TYPE_ADMIN = 2;
    private static final String CLIENT_ID = "leman-admin";
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemUserService userService;
    private final SystemMenuService menuService;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final cn.iocoder.yudao.module.system.service.user.HrIdentityService hrIdentityService;

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO, String loginIp, Long tenantId) {
        Long realTenantId = tenantId == null ? DEFAULT_TENANT_ID : tenantId;
        SystemUserDO user = userService.getUserByUsername(realTenantId, reqVO.getUsername());
        if (user == null || !passwordEncoder.matches(reqVO.getPassword(), user.getPassword())) {
            throw new ServiceException(1001001000, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new ServiceException(1001001001, "账号已被禁用");
        }
        userService.updateLoginInfo(user.getId(), loginIp);
        return buildTokenResp(tokenStore.create(buildLoginUser(user, hrIdentityService.isAdministrator(user.getTenantId(), user.getId()) ? menuService.getAllPermissions() : Set.of())));
    }

    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        TokenSession session = tokenStore.refresh(refreshToken);
        if (session == null) {
            throw new ServiceException(401, "无效的刷新令牌");
        }
        if (!hrIdentityService.isEnabled(session.getLoginUser())) {
            tokenStore.removeByAccessToken(session.getAccessToken());
            throw new ServiceException(401, "账号已失效");
        }
        return buildTokenResp(session);
    }

    @Override
    public void logout(String authorization) {
        String token = resolveToken(authorization);
        if (token != null) {
            tokenStore.removeByAccessToken(token);
        }
    }

    @Override
    public AuthPermissionInfoRespVO getPermissionInfo() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "未登录或登录已过期");
        }
        return AuthPermissionInfoRespVO.builder()
                .user(UserInfo.builder()
                        .id(loginUser.getId())
                        .username(loginUser.getUsername())
                        .nickname(loginUser.getNickname())
                        .deptId(loginUser.getDeptId())
                        .sex(0)
                        .avatar("")
                        .build())
                .roles(List.of(loginUser.isAdmin() ? "admin" : "employee"))
                .permissions(loginUser.getPermissions())
                .menus(loginUser.isAdmin() ? menuService.getRouteMenus() : List.of())
                .build();
    }

    private LoginUser buildLoginUser(SystemUserDO user, Set<String> permissions) {
        return LoginUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(user.getTenantId())
                .deptId(user.getDeptId())
                .admin(hrIdentityService.isAdministrator(user.getTenantId(), user.getId()))
                .permissions(permissions)
                .build();
    }

    private AuthLoginRespVO buildTokenResp(TokenSession session) {
        return AuthLoginRespVO.builder()
                .id(session.getId())
                .accessToken(session.getAccessToken())
                .refreshToken(session.getRefreshToken())
                .userId(session.getLoginUser().getId())
                .userType(USER_TYPE_ADMIN)
                .clientId(CLIENT_ID)
                .expiresTime(session.getExpiresTime())
                .requiresPasswordChange(hrIdentityService.requiresChange(session.getLoginUser().getTenantId(),session.getLoginUser().getId()))
                .administrator(session.getLoginUser().isAdmin())
                .build();
    }

    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private String formatLoginDate(SystemUserDO user) {
        return user.getLoginDate() == null ? null : DATE_TIME_FORMATTER.format(user.getLoginDate());
    }

}
