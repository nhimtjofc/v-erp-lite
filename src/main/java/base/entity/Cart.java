package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
@Getter
@Setter
public class Cart extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "subtotal_amount_minor", nullable = false)
    private long subtotalAmountMinor = 0;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<CartItem> items = new ArrayList<>();
}
