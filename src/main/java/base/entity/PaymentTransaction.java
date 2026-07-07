package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "gateway", "gateway_transaction_id"})
})
@Getter
@Setter
public class PaymentTransaction extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_intent_id", nullable = false)
    private PaymentIntent paymentIntent;

    @Column(nullable = false)
    private String gateway; // VNPAY, STRIPE, MOCK

    @Column(name = "gateway_transaction_id", nullable = false)
    private String gatewayTransactionId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status; // PENDING, SUCCESS, FAILED
}
