package base.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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
    // Current marketplace flow plus legacy order statuses retained for existing data.
    private String status; // PENDING_PAYMENT, PAID_ESCROW, OWNER_ACCEPTED, FULFILLING, DELIVERED, COMPLETED, OWNER_DECLINED, REFUNDED, PENDING, CONFIRMED, SHIPPING, SHIPPED, CANCELLED

    @Column(name = "owner_decision_deadline")
    private Instant ownerDecisionDeadline;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShippingName() {
        return shippingName;
    }

    public void setShippingName(String shippingName) {
        this.shippingName = shippingName;
    }

    public String getShippingPhone() {
        return shippingPhone;
    }

    public void setShippingPhone(String shippingPhone) {
        this.shippingPhone = shippingPhone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getShippingNote() {
        return shippingNote;
    }

    public void setShippingNote(String shippingNote) {
        this.shippingNote = shippingNote;
    }

    public long getSubtotalAmountMinor() {
        return subtotalAmountMinor;
    }

    public void setSubtotalAmountMinor(long subtotalAmountMinor) {
        this.subtotalAmountMinor = subtotalAmountMinor;
    }

    public long getDiscountAmountMinor() {
        return discountAmountMinor;
    }

    public void setDiscountAmountMinor(long discountAmountMinor) {
        this.discountAmountMinor = discountAmountMinor;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public long getShippingFeeMinor() {
        return shippingFeeMinor;
    }

    public void setShippingFeeMinor(long shippingFeeMinor) {
        this.shippingFeeMinor = shippingFeeMinor;
    }

    public long getTaxAmountMinor() {
        return taxAmountMinor;
    }

    public void setTaxAmountMinor(long taxAmountMinor) {
        this.taxAmountMinor = taxAmountMinor;
    }

    public BigDecimal getVatPercent() {
        return vatPercent;
    }

    public void setVatPercent(BigDecimal vatPercent) {
        this.vatPercent = vatPercent;
    }

    public long getVatAmountMinor() {
        return vatAmountMinor;
    }

    public void setVatAmountMinor(long vatAmountMinor) {
        this.vatAmountMinor = vatAmountMinor;
    }

    public long getFinalAmountMinor() {
        return finalAmountMinor;
    }

    public void setFinalAmountMinor(long finalAmountMinor) {
        this.finalAmountMinor = finalAmountMinor;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
