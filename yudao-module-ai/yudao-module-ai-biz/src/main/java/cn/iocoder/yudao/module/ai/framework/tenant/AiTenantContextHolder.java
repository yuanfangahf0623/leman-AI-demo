package cn.iocoder.yudao.module.ai.framework.tenant;

/**
 * AI module tenant context holder.
 */
public final class AiTenantContextHolder {

    private static final Long DEFAULT_TENANT_ID = 0L;
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private AiTenantContextHolder() {
    }

    public static Long getTenantId() {
        Long tenantId = TENANT_ID.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }

}
