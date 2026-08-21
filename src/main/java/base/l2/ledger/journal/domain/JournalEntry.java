package base.l2.ledger.journal.domain;

import base.l0.system.persistance.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One business event. Written only by {@code ledger_post_entry()}; append-only triggers reject any
 * update or delete, so this maps as read-only.
 */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
public class JournalEntry extends BaseEntity {

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "reverses_entry_uuid")
    private UUID reversesEntryUuid;

    @Column(name = "memo")
    private String memo;

    @Column(name = "metadata")
    private String metadata;
}

