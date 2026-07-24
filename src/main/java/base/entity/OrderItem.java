package base.entity;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "unit_cost_minor", nullable = false)
    private long unitCostMinor;

    @Column(name = "subtotal_amount_minor", nullable = false)
    private long subtotalAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    @Column(name = "return_requested_by")
    private UUID returnRequestedBy;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "return_approved_at")
    private Instant returnApprovedAt;

    @Column(name = "return_approved_by")
    private UUID returnApprovedBy;

    @Column(name = "return_status")
    private String returnStatus;

    @Column(name = "return_refund_minor")
    private Long returnRefundMinor;

    @Column(name = "return_refund_currency", length = 3)
    private String returnRefundCurrency;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(ProductVariant productVariant) {
        this.productVariant = productVariant;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public void setUnitPriceMinor(long unitPriceMinor) {
        this.unitPriceMinor = unitPriceMinor;
    }

    public long getSubtotalAmountMinor() {
        return subtotalAmountMinor;
    }

    public void setSubtotalAmountMinor(long subtotalAmountMinor) {
        this.subtotalAmountMinor = subtotalAmountMinor;
    }
}
