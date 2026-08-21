package base.l1.platform.reference;

import base.l0.system.security.CallerContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a reference — or a bank memo containing one — back into the document it names.
 *
 * <p>Reads are tenant-scoped explicitly, which is the behaviour we want: a statement line belongs
 * to one tenant's bank account, so a reference issued by another tenant must not resolve against it
 * even though the string space is global.
 *
 * <p><b>This is not authorisation.</b> The 35 bits of entropy in a reference make it unguessable,
 * and unguessable is not the same as private — the string is printed on bank statements and handed
 * to third-party banks. Anything that exposes what a reference resolves to needs its own access
 * control; the entropy is defence in depth behind that, never instead of it.
 */
@ApplicationScoped
public class ReferenceResolver {

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    CallerContext caller;

    /** The document a well-formed reference names, if we issued it. */
    public Optional<IssuedReference> resolve(String reference) {
        return ReferenceFormat.tryParse(reference).map(parsed -> byValue(parsed.value()));
    }

    /**
     * Every reference we recognise inside a bank memo.
     *
     * <p>Returns a list rather than one match because a memo can legitimately carry several, and
     * because silently picking one of two candidates is how a payment gets matched to the wrong
     * document. A caller that finds more than one should treat it as unmatched.
     */
    public List<IssuedReference> resolveFromMemo(String memo) {
        return ReferenceFormat.scan(memo).stream()
                .map(parsed -> byValue(parsed.value()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * The document a reference names, found without a tenant to look in.
     *
     * <p>{@link #resolve} cannot serve this because it requires the caller tenant. An inbound
     * webhook has no tenant until its reference resolves, so this narrowly-scoped native query is
     * the only cross-tenant lookup. A reference is unique across the whole service because a bank
     * memo arrives with nothing to disambiguate it.
     */
    @SuppressWarnings("unchecked")
    public Optional<ReferenceOwner> resolveAcrossTenants(String reference) {
        Optional<PaymentReference> parsed = ReferenceFormat.tryParse(reference);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT tenant_code, document_type, document_id FROM issued_references "
                                + "WHERE reference = ?1")
                .setParameter(1, parsed.get().value())
                .getResultList();
        return rows.stream().findFirst().map(r -> new ReferenceOwner(
                (String) r[0], (String) r[1], (UUID) r[2]));
    }

    /** Who a reference belongs to: the tenant that issued it and the document it names. */
    public record ReferenceOwner(String tenantCode, String documentType, UUID documentId) {
    }

    private IssuedReference byValue(String reference) {
        return IssuedReference.find("reference = ?1 and tenantCode = ?2", reference, caller.tenantCode())
                .firstResult();
    }
}
