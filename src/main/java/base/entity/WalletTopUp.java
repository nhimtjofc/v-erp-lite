package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "wallet_topups")
@Getter
@Setter
public class WalletTopUp extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "payment_qr_code_base64", columnDefinition = "TEXT")
    private String paymentQrCodeBase64;
}
