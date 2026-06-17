package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "is_active")
    private boolean active = true;

    public static boolean exists(String code) {
        return find("code", code).firstResult() != null;
    }
}
