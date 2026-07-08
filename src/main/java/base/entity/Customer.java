package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "card_number", unique = true)
    private String cardNumber;

    @Column(name = "points", nullable = false)
    private int points = 0;

    @Column(name = "total_spend_minor", nullable = false)
    private Long totalSpendMinor = 0L;

    @Column(name = "tier", nullable = false)
    private String tier = "SILVER";

    @PrePersist
    public void prePersist() {
        if (cardNumber == null || cardNumber.isBlank()) {
            cardNumber = generateCardNumber();
        }
        if (totalSpendMinor == null) {
            totalSpendMinor = 0L;
        }
        if (tier == null || tier.isBlank()) {
            tier = "SILVER";
        }
    }

    private String generateCardNumber() {
        long rand = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 100000000L);
        return "MEM-" + String.format("%04d-%04d", rand / 10000, rand % 10000);
    }
}
