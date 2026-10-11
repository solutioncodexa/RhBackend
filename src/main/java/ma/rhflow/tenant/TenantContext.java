package ma.rhflow.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantContext() {
    }

    public static void setTenantId(Long id) {
        TENANT_ID.set(id);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static Long requireTenantId() {
        Long id = TENANT_ID.get();
        if (id == null) {
            throw new IllegalStateException("Aucun tenant résolu pour cette requête");
        }
        return id;
    }

    public static void setBypass(boolean bypass) {
        BYPASS.set(bypass);
    }

    public static boolean isBypass() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    public static void clear() {
        TENANT_ID.remove();
        BYPASS.remove();
    }
}
