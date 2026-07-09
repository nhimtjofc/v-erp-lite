package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrow_records")
@Getter
@Setter
public class EscrowRecord extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_wallet_id", nullable = false)
    private Wallet sellerWallet;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status; // HELD, RELEASED, REFUNDED

    @Column(name = "release_due_at", nullable = false)
    private Instant releaseDueAt;
}
