package base.l1.platform.reference;

import base.l0.system.persistance.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * The registry row tying a reference we issued to the document it names.
 *
 * <p>Stores the literal string and nothing derived from it. The time bucket and entropy are
 * recoverable by parsing, and a stored copy of a derived value is a second source of truth that can
 * drift from the first. The generation is not stored either — it is the first character.
 */
@Entity
@Table(name = "issued_references")
@Getter
@Setter
public class IssuedReference extends BaseEntity {

    @Column(name = "reference", nullable = false, length = 35)
    private String reference;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;
}

