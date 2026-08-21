package base.l1.platform.reference;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issuing and resolving against the real schema and its constraints. */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ReferenceRegistryTest {

    @Inject
    ReferenceIssuer issuer;

    @Inject
    ReferenceResolver resolver;

    @Test
    void issuingTwiceForOneDocumentReturnsTheSameReference() {
        // A payout instructed, failed and retried must arrive at the bank as the same transfer, not
        // as a second unrelated one.
        UUID documentId = UUID.randomUUID();

        String first = issuer.issue("PAYOUT", documentId).getReference();
        String second = issuer.issue("PAYOUT", documentId).getReference();

        assertEquals(first, second);
    }

    @Test
    void differentDocumentsGetDifferentReferences() {
        String a = issuer.issue("PAYOUT", UUID.randomUUID()).getReference();
        String b = issuer.issue("PAYOUT", UUID.randomUUID()).getReference();

        assertNotEquals(a, b);
    }

    @Test
    void aReferenceResolvesBackToItsDocument() {
        UUID documentId = UUID.randomUUID();
        String reference = issuer.issue("PAYOUT", documentId).getReference();

        IssuedReference resolved = resolver.resolve(reference).orElseThrow();

        assertEquals(documentId, resolved.getDocumentId());
        assertEquals("PAYOUT", resolved.getDocumentType());
    }

    @Test
    void aReferenceResolvesEvenWhenTheMemoManglesIt() {
        UUID documentId = UUID.randomUUID();
        String reference = issuer.issue("PAYOUT", documentId).getReference();

        // Lower case, hyphenated, and buried in the text a bank wraps around it.
        String memo = "CTY TNHH ABC THANH TOAN "
                + reference.substring(0, 4).toLowerCase() + "-" + reference.substring(4).toLowerCase();

        List<IssuedReference> found = resolver.resolveFromMemo(memo);

        assertEquals(1, found.size());
        assertEquals(documentId, found.getFirst().getDocumentId());
    }

    @Test
    void anUnissuedButWellFormedReferenceResolvesToNothing() {
        // Shape and check symbol are necessary, not sufficient. Only the registry decides.
        String neverIssued = ReferenceFormat.mint(java.time.Instant.now()).value();

        assertTrue(resolver.resolve(neverIssued).isEmpty());
        assertTrue(resolver.resolveFromMemo("PAID " + neverIssued).isEmpty());
    }

    @Test
    void garbageResolvesToNothingRatherThanThrowing() {
        assertTrue(resolver.resolve("NOT-A-REFERENCE").isEmpty());
        assertTrue(resolver.resolveFromMemo("PAYMENT FOR INVOICE 12345").isEmpty());
        assertTrue(resolver.resolveFromMemo("").isEmpty());
    }

    @Test
    void issuedReferencesAreUniqueAcrossManyDocuments() {
        // Exercises the mint-and-check loop against the real global unique constraint.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            assertTrue(seen.add(issuer.issue("PAYOUT", UUID.randomUUID()).getReference()));
        }
    }
}

