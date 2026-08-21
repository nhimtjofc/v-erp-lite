package base.l0.system.security;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs system-initiated work as a named tenant.
 *
 * <p>Every other path here takes its tenant from a bearer token, which is what makes tenant
 * isolation structural. Some work has no bearer token and cannot: a provider's webhook is
 * authenticated by its signature, arrives with no idea which of our tenants it concerns, and only
 * becomes attributable once its reference is resolved. Something has to carry the answer from that
 * lookup into the work that follows.
 *
 * <p>This is an elevation, not a bypass. The discriminator is still applied to every query — it is
 * supplied explicitly instead of read from a claim, and only for the duration of one call. What it
 * would weaken, if used casually, is the property that a request can only ever see its own tenant.
 * So it is deliberately awkward: one entry point, mandatory cleanup, no nesting, and
 * {@code SingleWritePathTest} fails the build if anything outside the inbound dispatcher calls it.
 *
 * <p>Nesting is refused rather than stacked. A nested elevation means one tenant's work is running
 * inside another's, and there is no answer to "whose data is this" that is not a bug.
 */
@ApplicationScoped
public class SystemTenantContext {

    private static final ThreadLocal<String> ACTING = new ThreadLocal<>();

    /** The tenant this thread is currently acting as, if any. Consulted by the resolvers. */
    public static Optional<String> acting() {
        return Optional.ofNullable(ACTING.get());
    }

    public <T> T runAs(String tenantCode, Supplier<T> work) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("System work needs a tenant to act as");
        }
        if (ACTING.get() != null) {
            throw new IllegalStateException("Already acting as " + ACTING.get()
                    + "; nesting tenant elevation would run one tenant's work inside another's");
        }

        // Hibernate resolves the tenant when a session opens and CallerContext is request-scoped,
        // so both have to be able to see this before any work starts. Activating the context here
        // rather than assuming one keeps the scheduler and the request paths identical.
        boolean activated = !Arc.container().requestContext().isActive();
        if (activated) {
            Arc.container().requestContext().activate();
        }
        ACTING.set(tenantCode);
        try {
            return work.get();
        } finally {
            ACTING.remove();
            if (activated) {
                Arc.container().requestContext().terminate();
            }
        }
    }

    public void runAs(String tenantCode, Runnable work) {
        runAs(tenantCode, () -> {
            work.run();
            return null;
        });
    }
}

