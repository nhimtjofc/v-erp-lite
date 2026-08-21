package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
public class SubscriptionPlan extends PanacheEntityBase {

    @Id
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "product_slice", nullable = false, length = 32)
    private String productSlice;

    @Column(name = "monthly_price_minor", nullable = false)
    private long monthlyPriceMinor;

    @Column(name = "annual_price_minor", nullable = false)
    private long annualPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, Boolean> features = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits_config", columnDefinition = "json", nullable = false)
    private Map<String, Long> limits = new LinkedHashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
