package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tags")
@Getter
@Setter
public class Tag extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active")
    private boolean active = true;
}
