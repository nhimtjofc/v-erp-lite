package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

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

    public boolean isValid(BigDecimal orderSubtotal) {
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
        if (orderSubtotal != null && orderSubtotal.compareTo(minOrderValue) < 0) {
            return false;
        }
        return true;
    }

    public BigDecimal calculateDiscount(BigDecimal orderSubtotal) {
        if (!isValid(orderSubtotal)) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = BigDecimal.ZERO;
        if ("PERCENTAGE".equalsIgnoreCase(discountType)) {
            discount = orderSubtotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
            if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                discount = maxDiscountAmount;
            }
        } else if ("FIXED_AMOUNT".equalsIgnoreCase(discountType)) {
            discount = discountValue;
        }
        // Discount cannot exceed the subtotal itself
        if (discount.compareTo(orderSubtotal) > 0) {
            discount = orderSubtotal;
        }
        return discount;
    }
}
