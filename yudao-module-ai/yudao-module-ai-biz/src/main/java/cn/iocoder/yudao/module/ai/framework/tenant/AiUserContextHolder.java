package cn.iocoder.yudao.module.ai.framework.tenant;

/**
 * AI 模块当前用户上下文。
 *
 * <p>真实 yudao-cloud 接入时应替换为项目统一登录用户与租户上下文。</p>
 */
public final class AiUserContextHolder {

    private static final Long DEFAULT_USER_ID = 0L;
    private static final Long DEFAULT_DEPARTMENT_ID = 0L;
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> DEPARTMENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> NICKNAME = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ADMIN = new ThreadLocal<>();

    private AiUserContextHolder() {
    }

    public static Long getTenantId() {
        return AiTenantContextHolder.getTenantId();
    }

    public static Long getUserId() {
        Long userId = USER_ID.get();
        return userId != null ? userId : DEFAULT_USER_ID;
    }

    public static Long getDepartmentId() {
        Long departmentId = DEPARTMENT_ID.get();
        return departmentId != null ? departmentId : DEFAULT_DEPARTMENT_ID;
    }

    public static String getNickname() {
        return NICKNAME.get();
    }

    public static boolean isAdmin() {
        return Boolean.TRUE.equals(ADMIN.get());
    }

    public static void setUserContext(Long tenantId, Long userId, Long departmentId) {
        AiTenantContextHolder.setTenantId(tenantId);
        USER_ID.set(userId);
        DEPARTMENT_ID.set(departmentId);
        ADMIN.set(false);
    }

    public static void setUserContext(Long tenantId, Long userId, Long departmentId, boolean admin) {
        setUserContext(tenantId, userId, departmentId, null, admin);
    }

    public static void setUserContext(Long tenantId, Long userId, Long departmentId, String nickname, boolean admin) {
        AiTenantContextHolder.setTenantId(tenantId);
        USER_ID.set(userId);
        DEPARTMENT_ID.set(departmentId);
        NICKNAME.set(nickname);
        ADMIN.set(admin);
    }

    public static void clear() {
        USER_ID.remove();
        DEPARTMENT_ID.remove();
        NICKNAME.remove();
        ADMIN.remove();
        AiTenantContextHolder.clear();
    }

}
