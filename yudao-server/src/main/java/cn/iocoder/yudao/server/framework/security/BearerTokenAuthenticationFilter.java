package cn.iocoder.yudao.server.framework.security;

import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer Token 认证过滤器，同时把用户、租户上下文同步给 AI 模块。
 */
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenStore tokenStore;
    private final cn.iocoder.yudao.module.system.service.user.HrIdentityService hrIdentityService;

    public BearerTokenAuthenticationFilter(TokenStore tokenStore, cn.iocoder.yudao.module.system.service.user.HrIdentityService hrIdentityService) {
        this.tokenStore = tokenStore;
        this.hrIdentityService = hrIdentityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null) {
                TokenSession session = tokenStore.getByAccessToken(token);
                if (session != null) {
                    LoginUser loginUser = session.getLoginUser();
                    if (!hrIdentityService.isEnabled(loginUser)) {
                        response.sendError(401); return;
                    }
                    boolean needsChange = hrIdentityService.requiresChange(loginUser.getTenantId(),loginUser.getId());
                    if (!loginUser.isAdmin() && !cn.iocoder.yudao.module.system.service.user.HrIdentityService.allowedEmployeeRequest(
                            request.getMethod(), request.getRequestURI(), needsChange)) {
                        response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"msg\":\"" + (needsChange ? "请先修改初始密码" : "没有访问权限") + "\",\"data\":null}");
                        return;
                    }
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null, loginUser.getPermissions().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    AiUserContextHolder.setUserContext(loginUser.getTenantId(), loginUser.getId(),
                            loginUser.getDeptId(), loginUser.getNickname(), loginUser.isAdmin());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            AiUserContextHolder.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

}
