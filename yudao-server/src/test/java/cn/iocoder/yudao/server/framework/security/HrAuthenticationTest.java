package cn.iocoder.yudao.server.framework.security;

import cn.iocoder.yudao.module.system.dal.mysql.SystemHrIdentityMapper;
import cn.iocoder.yudao.module.system.service.user.HrIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrAuthenticationTest {
    @Test void administratorIsExplicitAndTenantScoped() {
        HrIdentityService service = new HrIdentityService(mock(SystemHrIdentityMapper.class));
        ReflectionTestUtils.setField(service, "administratorIds", "2");
        assertTrue(service.isAdministrator(1L, 2L));
        assertFalse(service.isAdministrator(1L, 1L));
        assertFalse(service.isAdministrator(2L, 2L));
    }

    @Test void forcedPasswordChangeBlocksDataAndAdminPaths() {
        assertTrue(HrIdentityService.allowedEmployeeRequest("PUT", "/admin-api/system/user/profile/update-password", true));
        assertFalse(HrIdentityService.allowedEmployeeRequest("GET", "/admin-api/shared-hr/people", true));
        assertFalse(HrIdentityService.allowedEmployeeRequest("POST", "/admin-api/system/user/create", false));
        assertFalse(HrIdentityService.allowedEmployeeRequest("GET", "/admin-api/system/user/page", false));
        assertFalse(HrIdentityService.allowedEmployeeRequest("GET", "/admin-api/ai/knowledge/document/page", false));
    }

    @Test void disabledAndFirstLoginTokensCannotReachBusinessController() throws Exception {
        TokenStore store = new TokenStore(new LemanAuthProperties());
        LoginUser user = LoginUser.builder().id(55L).tenantId(1L).admin(false).build();
        TokenSession session = store.create(user);
        HrIdentityService identity = mock(HrIdentityService.class);
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(store, identity);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/shared-hr/people");
        req.addHeader("Authorization", "Bearer " + session.getAccessToken());
        AtomicBoolean reached = new AtomicBoolean();
        MockHttpServletResponse disabled = new MockHttpServletResponse();
        filter.doFilter(req, disabled, (r,s) -> reached.set(true));
        assertEquals(401, disabled.getStatus()); assertFalse(reached.get());
        when(identity.isEnabled(user)).thenReturn(true);
        when(identity.requiresChange(1L, 55L)).thenReturn(true);
        MockHttpServletResponse initial = new MockHttpServletResponse();
        filter.doFilter(req, initial, (r,s) -> reached.set(true));
        assertEquals(403, initial.getStatus()); assertFalse(reached.get());
    }

    @Test void passwordChangeRevokesAccessAndRefreshOnlyForTargetTenantUser() {
        TokenStore store = new TokenStore(new LemanAuthProperties());
        TokenSession first = store.create(LoginUser.builder().id(55L).tenantId(1L).build());
        TokenSession second = store.create(LoginUser.builder().id(55L).tenantId(1L).build());
        TokenSession other = store.create(LoginUser.builder().id(55L).tenantId(2L).build());
        store.revokeUser(1L, 55L);
        assertNull(store.getByAccessToken(first.getAccessToken()));
        assertNull(store.refresh(first.getRefreshToken()));
        assertNull(store.getByAccessToken(second.getAccessToken()));
        assertNotNull(store.getByAccessToken(other.getAccessToken()));
    }
}
