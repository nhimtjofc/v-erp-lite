package base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
public class Voucher extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "discount_type", nullable = false, length = 50)
    private String discountType; // "FIXED_AMOUNT" or "PERCENTAGE"

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_value_minor", nullable = false)
    private long minOrderValueMinor = 0;

    @Column(name = "max_discount_amount_minor")
    private Long maxDiscountAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isValid(long orderSubtotal) {
        if (!active) {
            return false;
        }
        Instant now = Instant.now();
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return false;
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            return false;
        }
        if (orderSubtotal < minOrderValueMinor) {
            return false;
        }
        return true;
    }

    public long calculateDiscount(long orderSubtotal) {
        if (!isValid(orderSubtotal)) {
            return 0;
        }
        long discount = 0;
        if ("PERCENTAGE".equalsIgnoreCase(discountType)) {
            discount = Math.round((double) orderSubtotal * discountValue / 100.0);
            if (maxDiscountAmountMinor != null && discount > maxDiscountAmountMinor) {
                discount = maxDiscountAmountMinor;
            }
        } else if ("FIXED_AMOUNT".equalsIgnoreCase(discountType)) {
            discount = discountValue;
        }
        // Discount cannot exceed the subtotal itself
        if (discount > orderSubtotal) {
            discount = orderSubtotal;
        }
        return discount;
    }
}
