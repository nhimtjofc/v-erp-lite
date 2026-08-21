package base.l2.ledger.journal.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A complete entry awaiting posting.
 *
 * <p>{@code idempotencyKey} is not advisory. It is a unique key in the database, so a replayed
 * webhook or a double-submitted request collides rather than posting twice — the check and the
 * write are the same operation, with no window between them.
 */
public record PostingRequest(
        UUID entryUuid,
        String eventType,
        String documentType,
        UUID documentId,
        String provider,
        String providerRef,
        String idempotencyKey,
        Instant effectiveAt,
        UUID reversesEntryUuid,
        String memo,
        Map<String, Object> metadata,
        List<PostingLine> lines
) {
    public static Builder of(String eventType, String idempotencyKey) {
        return new Builder(eventType, idempotencyKey);
    }

    public static final class Builder {
        private final String eventType;
        private final String idempotencyKey;
        private UUID entryUuid;
        private String documentType;
        private UUID documentId;
        private String provider;
        private String providerRef;
        private Instant effectiveAt;
        private UUID reversesEntryUuid;
        private String memo;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final List<PostingLine> lines = new java.util.ArrayList<>();

        private Builder(String eventType, String idempotencyKey) {
            this.eventType = eventType;
            this.idempotencyKey = idempotencyKey;
        }

        public Builder entryUuid(UUID value) {
            this.entryUuid = value;
            return this;
        }

        public Builder document(String type, UUID id) {
            this.documentType = type;
            this.documentId = id;
            return this;
        }

        public Builder provider(String name, String ref) {
            this.provider = name;
            this.providerRef = ref;
            return this;
        }

        public Builder effectiveAt(Instant value) {
            this.effectiveAt = value;
            return this;
        }

        public Builder reverses(UUID entry) {
            this.reversesEntryUuid = entry;
            return this;
        }

        public Builder memo(String value) {
            this.memo = value;
            return this;
        }

        /**
         * Tags the entry so it can be reconciled as part of a group. Every entry carrying the same
         * group must sum to zero once the lifecycle it describes is complete; a group that does not
         * is a discrepancy that names itself.
         */
        public Builder reconcileGroup(String value) {
            this.metadata.put("reconcileGroup", value);
            return this;
        }

        public Builder meta(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder line(PostingLine value) {
            this.lines.add(value);
            return this;
        }

        public PostingRequest build() {
            return new PostingRequest(
                    entryUuid, eventType, documentType, documentId, provider, providerRef,
                    idempotencyKey, effectiveAt, reversesEntryUuid, memo,
                    Map.copyOf(metadata), List.copyOf(lines));
        }
    }
}

