package base.l2.ledger.fuzz;

import base.l0.system.money.Money;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.provider.inbox.ProviderEventRecord;
import base.l1.platform.provider.inbox.RecordProviderEventUC;
import base.l1.platform.reference.IssuedReference;
import base.l1.platform.reference.ReferenceIssuer;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two remaining find-then-insert paths, driven concurrently.
 *
 * <p>Both are guarded by a unique constraint, so the worst case is a spurious error rather than
 * corruption — which is exactly why neither had a test. But both sit on paths where a spurious
 * error is expensive: a reference is minted inside the payout that needs it, and a webhook that
 * answers 500 gets redelivered, and enough of those get an endpoint disabled.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ConcurrentIdempotencyTest {

    private static final int THREADS = 8;

    @Inject
    ReferenceIssuer references;

    @Inject
    RecordProviderEventUC inbox;

    @Inject
    EntityManager em;

    /** Issuing for one document from several transactions must converge, not collide. */
    @Test
    void concurrentIssuesForOneDocumentAgreeOnOneReference() throws Exception {
        UUID documentId = UUID.randomUUID();
        ConcurrentLinkedQueue<String> issued = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

        runConcurrently(index -> {
            IssuedReference reference = references.issue("PAYOUT", documentId);
            issued.add(reference.getReference());
        }, failures);

        System.out.printf("IDEM-PROBE issue-same-doc succeeded=%d failed=%d distinctReferences=%d%n",
                issued.size(), failures.size(), new HashSet<>(issued).size());
        failures.stream().distinct().forEach(f -> System.out.println("IDEM-REASON " + f));

        assertEquals(List.of(), new ArrayList<>(failures),
                "a reference is minted inside the payout that needs it; a lost race takes the payout with it");
        assertEquals(1, new HashSet<>(issued).size(),
                "every caller must be told the same reference — two references for one document "
                        + "means two strings in two bank memos for one transfer");
        assertEquals(1L, referenceRowsFor(documentId));
    }

    /** Issuing for different documents at once must never hand out the same string. */
    @Test
    void concurrentIssuesForDifferentDocumentsNeverCollide() throws Exception {
        ConcurrentLinkedQueue<String> issued = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

        runConcurrently(index -> issued.add(
                references.issue("PAYOUT", UUID.randomUUID()).getReference()), failures);

        System.out.printf("IDEM-PROBE issue-many-docs succeeded=%d failed=%d distinctReferences=%d%n",
                issued.size(), failures.size(), new HashSet<>(issued).size());

        assertEquals(List.of(), new ArrayList<>(failures));
        assertEquals(THREADS, new HashSet<>(issued).size(), "references must be globally unique");
    }

    /** The same webhook delivered twice at once must be stored once and accepted twice. */
    @Test
    void concurrentRedeliveryOfOneEventIsStoredOnceAndAcceptedEveryTime() throws Exception {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        ProviderEvent event = new ProviderEvent(
                "localstripe", eventId, "P0786ABCDEF1", ProviderEvent.Signal.PAYOUT_CONFIRMED,
                Money.of(1_000, "EUR"), Instant.now(), "{\"id\":\"" + eventId + "\"}");

        Set<UUID> storedUuids = java.util.concurrent.ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

        runConcurrently(index -> storedUuids.add(inbox.record(event).getUuid()), failures);

        System.out.printf("IDEM-PROBE webhook-redelivery threads=%d failed=%d distinctRows=%d%n",
                THREADS, failures.size(), storedUuids.size());
        failures.stream().distinct().forEach(f -> System.out.println("IDEM-REASON " + f));

        assertEquals(List.of(), new ArrayList<>(failures),
                "a provider retries on any non-2xx, so a duplicate delivery answering 500 becomes "
                        + "a redelivery loop and eventually a disabled endpoint");
        assertEquals(1, storedUuids.size(), "every caller must be handed the same stored event");
        assertEquals(1L, eventRowsFor(eventId));
    }

    // ---- helpers ----------------------------------------------------------------------

    private interface Work {
        void run(int index) throws Exception;
    }

    private void runConcurrently(Work work, ConcurrentLinkedQueue<String> failures) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try (ExecutorService workers = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                final int index = i;
                workers.submit(() -> {
                    Arc.container().requestContext().activate();
                    try {
                        start.await();
                        work.run(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        Throwable root = e;
                        while (root.getCause() != null) {
                            root = root.getCause();
                        }
                        failures.add(root.getClass().getSimpleName() + ": " + root.getMessage());
                    } finally {
                        Arc.container().requestContext().terminate();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish");
        }
    }

    @Transactional
    long referenceRowsFor(UUID documentId) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COUNT(*) FROM issued_references WHERE document_id = ?1")
                .setParameter(1, documentId)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    long eventRowsFor(String eventId) {
        em.clear();
        return ProviderEventRecord.count("providerEventId = ?1", eventId);
    }
}

