package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "business")
@Getter
@Setter
public class Business extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Column
    private String phone;

    @Column
    private String email;

    @Column
    private String taxId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "is_active")
    private boolean active = true;
}
