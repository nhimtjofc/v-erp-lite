package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inbound_event_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_gateway_event",
                        columnNames = {"gateway", "event_id"}
                )
        }
)
@Getter
@Setter
public class InboundEventLog extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
