package base.l0;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The database's SQL mode is a choice this service makes, not one it inherits.
 *
 * <p>MariaDB 11.4 defaults to something strict enough, which is exactly why this is worth
 * asserting: "the version we happen to run today behaves correctly" is not a property of the
 * service. Against a server configured leniently — an older major, a managed instance with its own
 * defaults, a DBA who relaxed it for a migration and left it — silent coercion returns.
 *
 * <p>The case that matters here is truncation. {@code idempotency_key} is {@code VARCHAR(200)}, and
 * two distinct keys sharing a 200-character prefix would be cut to the same value under a lenient
 * mode. The unique constraint would then do exactly what it is there for, on the wrong data, and
 * the second payment would be recorded as a duplicate of the first.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class SqlModeTest {

    @Inject
    EntityManager em;

    @Test
    void theSessionRunsInStrictMode() {
        String mode = (String) em.createNativeQuery("SELECT @@session.sql_mode").getSingleResult();
        assertTrue(mode.contains("STRICT_ALL_TABLES"),
                "the pinned mode should reach the session, but it is: " + mode);
        assertTrue(mode.contains("NO_ZERO_DATE"), mode);
    }

    @Test
    void anOverlongValueIsRefusedRatherThanTruncated() {
        String tooLong = "k".repeat(260);

        // Not a hypothetical: silently cutting this to 200 characters is how two different events
        // come to share an idempotency key, and the ledger then refuses the second as a replay of
        // the first while both were real.
        assertThrows(RuntimeException.class, () -> insertEntry(tooLong));
    }

    @Transactional
    void insertEntry(String idempotencyKey) {
        em.createNativeQuery(
                        "INSERT INTO journal_entries (uuid, tenant_code, event_type, idempotency_key, "
                                + "effective_at, created_at) VALUES (?1, 'acme', 'MANUAL_ADJUSTMENT', ?2, "
                                + "NOW(6), NOW(6))")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, idempotencyKey)
                .executeUpdate();
        em.flush();
    }
}

