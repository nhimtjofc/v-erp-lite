package base.l2.settlement;

import base.l0.system.money.Money;
import base.l1.platform.reference.IssuedReference;
import base.l1.platform.reference.ReferenceFormat;
import base.l1.platform.reference.ReferenceIssuer;
import base.l1.platform.reference.ReferenceResolver;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.payout.application.ConfirmPayoutUC;
import base.l2.settlement.payout.application.InstructPayoutUC;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbound half of reconciliation: a transaction we initiate carries a reference we minted, in
 * the column that is indexed for it.
 *
 * <p>Before this, {@code bankRef} reached the ledger only inside memo prose — {@code provider} and
 * {@code provider_ref} were left null on every payout entry, so the join key described in V0.001 as
 * "join key for provider settlement files and bank statements" was empty on exactly the entries an
 * outbound reference exists for. Finding a payout by its bank reference meant a LIKE scan over free
 * text, and a returned transfer recorded no reference at all.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class PayoutReferenceTest {

    @Inject
    InstructPayoutUC instructPayout;

    @Inject
    ConfirmPayoutUC confirmPayout;

    @Inject
    ReferenceIssuer issuer;

    @Inject
    ReferenceResolver resolver;

    @Inject
    PostEntryUC postEntry;

    @Inject
    EntityManager em;

    @Inject
    LedgerTestFixture fixture;

    private LedgerAccount fundedSeller(UUID sellerId, long amountMinor) {
        LedgerAccount seller = fixture.seller(sellerId, "VND", 500_000);
        LedgerAccount psp = fixture.psp("VND");
        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "test-fund:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(amountMinor, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(amountMinor, "VND")))
                .build());
        return seller;
    }

    @Test
    void everyPayoutEntryCarriesOurReferenceInTheIndexedColumn() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        fundedSeller(sellerId, 300_000);
        fixture.bank("VND");

        JournalEntry instructed = instructPayout.instruct(payoutId, sellerId, Money.of(200_000, "VND"), "BANKREF-1");
        String reference = issuer.issue("PAYOUT", payoutId).getReference();

        assertEquals("bank", instructed.getProvider());
        assertEquals(reference, instructed.getProviderRef(), "the instruction must name our reference");

        JournalEntry confirmed = confirmPayout.confirm(payoutId, Money.of(200_000, "VND"), "BANKREF-1");
        assertEquals(reference, confirmed.getProviderRef(), "and so must the confirmation");

        // Every leg of the lifecycle is findable by the string that travelled with the money.
        assertEquals(2, entriesByProviderRef(reference).size());
    }

    @Test
    void aReturnedTransferRecordsTheReferenceItReverses() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        fundedSeller(sellerId, 300_000);
        fixture.bank("VND");

        JournalEntry instructed = instructPayout.instruct(payoutId, sellerId, Money.of(200_000, "VND"), null);
        JournalEntry returned = confirmPayout.markReturned(
                payoutId, sellerId, Money.of(200_000, "VND"), instructed.getUuid(), "account closed");

        assertNotNull(returned.getProviderRef(), "a return with no reference cannot be tied to what it undoes");
        assertEquals(instructed.getProviderRef(), returned.getProviderRef());
    }

    @Test
    void aStatementMemoLeadsBackToThePayout() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        fundedSeller(sellerId, 300_000);
        fixture.bank("VND");

        JournalEntry instructed = instructPayout.instruct(payoutId, sellerId, Money.of(150_000, "VND"), null);
        String reference = instructed.getProviderRef();

        // What comes back on the statement, with the bank's own text wrapped around it.
        List<IssuedReference> found = resolver.resolveFromMemo(
                "IBFT " + reference + " CHUYEN TIEN CTY ABC");

        assertEquals(1, found.size());
        assertEquals(payoutId, found.getFirst().getDocumentId());
        assertEquals("PAYOUT", found.getFirst().getDocumentType());
    }

    @Test
    void theReferenceIsShortEnoughForTheTightestRail() {
        UUID payoutId = UUID.randomUUID();
        String reference = issuer.issue("PAYOUT", payoutId).getReference();

        // New Zealand's domestic EFT fields are 12 characters and are the binding constraint
        // worldwide; VietQR is ~19, UK Faster Payments and Australian BECS 18.
        assertTrue(reference.length() <= 12, "reference must fit a 12-character memo field: " + reference);
        assertTrue(reference.chars().allMatch(Character::isLetterOrDigit),
                "special characters are rejected outright by VietQR: " + reference);
        assertEquals(reference.toUpperCase(), reference);
        assertTrue(ReferenceFormat.tryParse(reference).isPresent());
    }

    @SuppressWarnings("unchecked")
    @Transactional
    List<Object[]> entriesByProviderRef(String reference) {
        return em.createNativeQuery("""
                SELECT uuid, event_type FROM journal_entries
                 WHERE tenant_code = 'acme' AND provider = 'bank' AND provider_ref = ?1
                """).setParameter(1, reference).getResultList();
    }
}

