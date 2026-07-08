package base.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate = Instant.now();

    @Column(nullable = false, length = 50)
    private String status; // PENDING, CONFIRMED, SHIPPED, COMPLETED, CANCELLED

    @Column(name = "shipping_name", nullable = false)
    private String shippingName;

    @Column(name = "shipping_phone", nullable = false, length = 50)
    private String shippingPhone;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "shipping_note", columnDefinition = "TEXT")
    private String shippingNote;

    @Column(name = "subtotal_amount_minor", nullable = false)
    private long subtotalAmountMinor;

    @Column(name = "discount_amount_minor", nullable = false)
    private long discountAmountMinor = 0;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "shipping_fee_minor", nullable = false)
    private long shippingFeeMinor = 0;

    @Column(name = "tax_amount_minor", nullable = false)
    private long taxAmountMinor = 0;

    @Column(name = "vat_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatPercent = BigDecimal.ZERO;

    @Column(name = "vat_amount_minor", nullable = false)
    private long vatAmountMinor = 0;

    @Column(name = "final_amount_minor", nullable = false)
    private long finalAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();
}
