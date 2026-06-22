package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, Object> settings = new HashMap<>();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserSettings findByUserId(UUID userId) {
        return find("userId", userId).firstResult();
    }
}
