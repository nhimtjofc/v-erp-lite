package base.l1.platform.reference;

import base.l0.system.persistance.DuplicateKeys;
import base.l0.system.security.CallerContext;
import base.l0.system.util.UuidUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues the reference for a document, or returns the one it already has.
 *
 * <p>Idempotent by design: a payout instructed, failed and retried must carry the same reference
 * both times, or the second attempt arrives at the bank as an unrelated transfer. That is enforced
 * by a unique constraint on {@code (tenant_code, document_type, document_id)} rather than by
 * remembering to check.
 */
@ApplicationScoped
public class ReferenceIssuer {

    /**
     * Generation 1 puts a collision at roughly one insert in 344,000. Five attempts takes the odds
     * of exhausting them to somewhere past one in 10^26, and a bounded loop cannot hang.
     */
    private static final int MAX_ATTEMPTS = 5;

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    public IssuedReference issue(String documentType, UUID documentId) {
        return issue(documentType, documentId, Instant.now());
    }

    /**
     * Idempotent per document, including when two callers ask at the same moment.
     *
     * <p>The check-then-insert below cannot be made race-free by checking harder: two transactions
     * both read "no reference yet" before either writes, and
     * {@code uq_issued_references_doc} rejects the loser. Measured at eight concurrent calls for one
     * document, seven came back as duplicate-key violations — and because this runs inside the
     * payout that needs the reference, each of those would have rolled the payout back over a
     * question that already had an answer.
     *
     * <p>So a duplicate is read rather than raised: the winner has committed by the time the loser
     * sees the error, and its reference is the one both callers want. One caller must be told one
     * string, because the alternative is two references for one transfer and two different memos
     * for the money.
     */
    public IssuedReference issue(String documentType, UUID documentId, Instant at) {
        IssuedReference existing = forDocument(documentType, documentId);
        if (existing != null) {
            return existing;
        }
        try {
            return mintAndStore(documentType, documentId, at);
        } catch (RuntimeException e) {
            if (!DuplicateKeys.isDuplicate(e)) {
                throw e;
            }
            IssuedReference winner = forDocument(documentType, documentId);
            if (winner == null) {
                // Not the document constraint then — a minted string collided with one already
                // issued, which the retry loop is for. Re-running it draws fresh entropy.
                return mintAndStore(documentType, documentId, at);
            }
            return winner;
        }
    }

    /**
     * REQUIRES_NEW so a losing race is recoverable, and so a reference outlives a failed caller.
     *
     * <p>A constraint violation marks its transaction rollback-only, so the recovery read has to
     * happen outside the transaction that failed — joining the caller's would leave nothing to
     * recover into. The side effect is that a reference minted for a payout that then fails to post
     * stays in the table, unused. That is the right way round: a reference is never reused, so a
     * burned one costs nothing, and the retried payout finds this row and travels under the same
     * string it would have used the first time.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    IssuedReference mintAndStore(String documentType, UUID documentId, Instant at) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PaymentReference candidate = ReferenceFormat.mint(at);
            // Checked across every tenant, because the uniqueness constraint is global — a string in
            // a bank memo arrives with no tenant to disambiguate it. A native query is the only way
            // to see past the discriminator Hibernate appends to everything else.
            if (isTaken(candidate.value())) {
                continue;
            }
            IssuedReference issued = new IssuedReference();
            issued.setUuid(UuidUtil.v7());
            issued.setTenantCode(caller.tenantCode());
            issued.setReference(candidate.value());
            issued.setDocumentType(documentType);
            issued.setDocumentId(documentId);
            issued.setCreatedBy(caller.actorId());
            issued.persist();
            // Forced now so a collision surfaces inside the retry loop that exists to absorb it,
            // rather than at commit where there is no attempt left to make.
            IssuedReference.flush();
            return issued;
        }

        throw new IllegalStateException(
                "Could not mint a free reference in " + MAX_ATTEMPTS + " attempts; entropy is exhausted "
                        + "for this bucket and the generation needs more random characters");
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public IssuedReference forDocument(String documentType, UUID documentId) {
        return IssuedReference.find("documentType = ?1 and documentId = ?2 and tenantCode = ?3", documentType, documentId, caller.tenantCode())
                .firstResult();
    }

    private boolean isTaken(String reference) {
        Object count = em.createNativeQuery("SELECT COUNT(*) FROM issued_references WHERE reference = ?1")
                .setParameter(1, reference)
                .getSingleResult();
        return ((Number) count).longValue() > 0;
    }
}
