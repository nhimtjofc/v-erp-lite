package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_checkpoints")
@Getter
@Setter
public class LedgerCheckpoint extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "last_transaction_id", nullable = false)
    private UUID lastTransactionId;

    @Column(name = "last_sequence_no", nullable = false)
    private long lastSequenceNo;

    @Column(name = "available_balance_minor", nullable = false)
    private long availableBalanceMinor;

    @Column(name = "pending_balance_minor", nullable = false)
    private long pendingBalanceMinor;

    @Column(name = "checkpointed_at", nullable = false)
    private Instant checkpointedAt = Instant.now();
}
