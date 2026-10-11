package ma.rhflow.tenant;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
@FilterDef(
        name = TenantScoped.FILTER_NAME,
        parameters = @ParamDef(name = TenantScoped.PARAM_NAME, type = Long.class)
)
public abstract class TenantScoped {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM_NAME = "tenantId";

    @Column(name = "tenant_id")
    private Long tenantId;
}
