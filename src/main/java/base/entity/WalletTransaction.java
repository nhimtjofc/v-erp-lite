package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
public class WalletTransaction extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String type; // CREDIT, DEBIT

    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // DEPOSIT, WITHDRAWAL, COMMISSION, ESCROW_HOLD, ESCROW_RELEASE, ESCROW_REFUND, PAYOUT

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "correction_of_transaction_id")
    private UUID correctionOfTransactionId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(columnDefinition = "TEXT")
    private String description;
}
