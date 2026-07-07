package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "wallets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "owner_type", "owner_id", "currency"})
})
@Getter
@Setter
public class Wallet extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "owner_type", nullable = false)
    private String ownerType; // SELLER, ADMIN, PLATFORM

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "available_balance_minor", nullable = false)
    private long availableBalanceMinor = 0;

    @Column(name = "pending_balance_minor", nullable = false)
    private long pendingBalanceMinor = 0;

    @Column(nullable = false, length = 3)
    private String currency = "USD";
}
