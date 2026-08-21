package base.l2.ledger.journal.application;

import base.l0.system.money.CurrencyMismatchException;
import base.l0.system.security.CallerContext;
import base.l2.ledger.journal.domain.DuplicateEntryException;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRejectedException;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.journal.domain.UnbalancedEntryException;
import base.l0.system.util.UuidUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only way money moves.
 *
 * <p>This is a thin wrapper over the {@code ledger_post_entry} stored procedure, and that is
 * deliberate. The rules that must never be broken — an entry balances, an account's balance is only
 * ever changed by a posting, a line is never edited, a sequence number is never reused — live in
 * the database, where no future code path, migration or repair script can route around them. The
 * predecessor kept those rules in one careful service class and relied on every caller going
 * through it; a review found five money-touching queries that had already stopped doing so.
 *
 * <p>What this class adds is a good error message. It validates the same rules first so a caller
 * gets {@link UnbalancedEntryException} naming the offending currency rather than a SQLSTATE 45000
 * from three layers down. The database check is the one that counts; this one is for humans.
 */
@ApplicationScoped
public class PostEntryUC {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    /**
     * Posts one balanced entry and returns it.
     *
     * @throws UnbalancedEntryException  debits do not equal credits in some currency
     * @throws DuplicateEntryException   the idempotency key has already been posted
     * @throws PostingRejectedException  the database refused the posting on policy grounds
     */
    @Transactional
    public JournalEntry post(PostingRequest request) {
        validate(request);

        UUID entryUuid = request.entryUuid() != null ? request.entryUuid() : UuidUtil.v7();
        Instant effectiveAt = request.effectiveAt() != null ? request.effectiveAt() : Instant.now();

        try {
            em.createNativeQuery("CALL ledger_post_entry(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                    .setParameter(1, entryUuid)
                    .setParameter(2, caller.tenantCode())
                    .setParameter(3, request.eventType())
                    .setParameter(4, request.documentType())
                    .setParameter(5, request.documentId())
                    .setParameter(6, request.provider())
                    .setParameter(7, request.providerRef())
                    .setParameter(8, request.idempotencyKey())
                    .setParameter(9, java.sql.Timestamp.from(effectiveAt))
                    .setParameter(10, request.reversesEntryUuid())
                    .setParameter(11, request.memo())
                    .setParameter(12, toJson(request.metadata()))
                    .setParameter(13, caller.actorId())
                    .setParameter(14, linesAsJson(request.lines()))
                    .executeUpdate();
        } catch (PersistenceException e) {
            throw translate(e, request);
        }

        em.clear();
        return JournalEntry.find("uuid = ?1 and tenantCode = ?2", entryUuid, caller.tenantCode()).firstResult();
    }

    /**
     * Validation mirroring what the procedure enforces, run first purely so failures are legible.
     * Anything this misses the database still catches.
     */
    private void validate(PostingRequest request) {
        List<PostingLine> lines = request.lines();
        if (lines.size() < 2) {
            throw new UnbalancedEntryException("A journal entry needs at least two lines, got " + lines.size());
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new UnbalancedEntryException("A journal entry needs an idempotency key");
        }

        Map<String, Long> netByCurrency = new LinkedHashMap<>();
        for (PostingLine line : lines) {
            if (!line.amount().isPositive()) {
                throw new UnbalancedEntryException(
                        "Line amounts must be positive; direction carries the sign. Got " + line.amount());
            }
            netByCurrency.merge(line.amount().currency(), line.signedMinor(), Long::sum);
        }
        netByCurrency.forEach((currency, net) -> {
            if (net != 0L) {
                throw new UnbalancedEntryException(
                        "Entry does not balance in " + currency + ": debits minus credits = " + net);
            }
        });
    }

    private String linesAsJson(List<PostingLine> lines) {
        List<Map<String, Object>> payload = new ArrayList<>(lines.size());
        for (PostingLine line : lines) {
            Map<String, Object> row = new HashMap<>();
            row.put("lineUuid", (line.lineUuid() != null ? line.lineUuid() : UuidUtil.v7()).toString());
            row.put("accountUuid", line.accountUuid().toString());
            row.put("direction", line.direction().name());
            row.put("amountMinor", line.amount().amountMinor());
            row.put("currency", line.amount().currency());
            row.put("dueDate", line.dueDate() == null ? null : line.dueDate().toString());
            row.put("memo", line.memo());
            payload.add(row);
        }
        return toJson(payload);
    }

    private String toJson(Object value) {
        if (value == null || (value instanceof Map<?, ?> map && map.isEmpty())) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise posting payload", e);
        }
    }

    /**
     * Maps the database's refusal onto a typed exception. The duplicate-key case is separated out
     * because it is not an error to the caller — it means a replay, and a replay is success.
     */
    private RuntimeException translate(PersistenceException e, PostingRequest request) {
        String message = rootMessage(e);
        if (e.getCause() instanceof ConstraintViolationException || message.contains("Duplicate entry")) {
            if (message.contains("uq_journal_entries_idem")) {
                return new DuplicateEntryException(request.idempotencyKey());
            }
        }
        if (message.contains("Unbalanced entry")) {
            return new UnbalancedEntryException(message);
        }
        if (message.contains("currency does not match")) {
            return new CurrencyMismatchException("line", "account");
        }
        return new PostingRejectedException(message, e);
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        String message = e.getMessage();
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
            if (cursor.getMessage() != null) {
                message = cursor.getMessage();
            }
        }
        return message == null ? "Posting rejected" : message;
    }
}
