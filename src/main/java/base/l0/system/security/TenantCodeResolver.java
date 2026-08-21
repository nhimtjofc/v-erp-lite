package base.l0.system.security;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Supplies the tenant discriminator Hibernate appends to every query on a {@code BaseEntity}.
 *
 * <p>This is the mechanism that makes tenant isolation structural rather than a convention. The
 * system this replaces filtered by hand — every query had to remember {@code and tenantId = ?} — and
 * a review found five money-touching queries that had forgotten to, including one that resolved a
 * top-up by id with no tenant predicate at all. A discriminator cannot be left off.
 *
 * <p>Scheduled and batch work runs with no request context and resolves to the configured default.
 * Those paths read deliberately across tenants and use native queries naming the tenant explicitly,
 * so they do not rely on the discriminator.
 */
@ApplicationScoped
@PersistenceUnitExtension
public class TenantCodeResolver implements TenantResolver {

    public static final String SYSTEM = "__system__";

    @Inject
    CallerContext caller;

    @ConfigProperty(name = "payment.tenant.default", defaultValue = SYSTEM)
    String defaultTenant;

    @Override
    public String getDefaultTenantId() {
        return defaultTenant;
    }

    @Override
    public String resolveTenantId() {
        // An explicit elevation wins: system work that has resolved which tenant it concerns is
        // more authoritative than the absent claim it would otherwise fall back from.
        Optional<String> acting = SystemTenantContext.acting();
        if (acting.isPresent()) {
            return acting.get();
        }
        if (!Arc.container().requestContext().isActive()) {
            return defaultTenant;
        }
        try {
            return caller.tenantCode();
        } catch (RuntimeException e) {
            return defaultTenant;
        }
    }
}

