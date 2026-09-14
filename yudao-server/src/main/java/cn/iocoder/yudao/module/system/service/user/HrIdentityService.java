package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.dal.mysql.SystemHrIdentityMapper;
import cn.iocoder.yudao.server.framework.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HrIdentityService {
    private final SystemHrIdentityMapper mapper;
    @Value("${leman.auth.administrator-user-ids:1}")
    private String administratorIds;
    @Value("${leman.auth.hr-login-hmac-key:}")
    private String hmacKey;

    public boolean isAdministrator(Long tenant, Long id) {
        return Long.valueOf(1).equals(tenant) && Arrays.stream(administratorIds.split(","))
                .map(String::trim).anyMatch(String.valueOf(id)::equals);
    }
    public Long resolveNationalId(Long tenant, String input) {
        String normalized = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[1-9][0-9]{16}[0-9X]") || hmacKey.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(hmacKey), "HmacSHA256"));
            String digest = HexFormat.of().formatHex(mac.doFinal((tenant + ":" + normalized).getBytes(StandardCharsets.UTF_8)));
            return mapper.findUserId(tenant, digest);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Login identity configuration is unavailable");
        }
    }
    public boolean isImported(Long tenant, Long user) { return mapper.isImported(tenant,user)>0; }
    public boolean requiresChange(Long tenant, Long user) { return mapper.requiresChange(tenant,user)>0; }
    public boolean isEnabled(LoginUser user) { return mapper.isEnabled(user.getTenantId(),user.getId())>0; }
    public void passwordChanged(Long tenant, Long user) { mapper.clearChange(tenant,user); }

    public static boolean allowedEmployeeRequest(String method, String path, boolean needsChange) {
        if (method.equals("POST") && path.equals("/admin-api/system/auth/logout")) return true;
        if (method.equals("PUT") && path.equals("/admin-api/system/user/profile/update-password")) return true;
        if (needsChange) return false;
        if (method.equals("GET") && Set.of("/admin-api/system/auth/get-permission-info",
                "/admin-api/system/user/profile/get", "/admin-api/system/dict-data/simple-list",
                "/admin-api/shared-hr/organizations", "/admin-api/shared-hr/people", "/admin-api/shared-hr/status").contains(path)) return true;
        return false;
    }
}
