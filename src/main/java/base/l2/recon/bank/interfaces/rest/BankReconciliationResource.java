package base.l2.recon.bank.interfaces.rest;

import base.l0.system.api.ApiResp;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.recon.bank.application.AttestBankBalanceUC;
import base.l2.recon.bank.application.BankReconciliationQuery;
import base.l2.recon.bank.application.ReclassifySuspenseUC;
import base.l2.recon.bank.application.RecordBankTransactionUC;
import base.l2.recon.bank.domain.BankStatementAttestation;
import base.l2.recon.bank.domain.BankTransaction;
import base.l2.recon.bank.interfaces.dto.AttestBalanceRequest;
import base.l2.recon.bank.interfaces.dto.BankAttestationDto;
import base.l2.recon.bank.interfaces.dto.ReclassifyRequest;
import base.l2.recon.bank.interfaces.dto.RecordBankTransactionRequest;
import base.l2.recon.bank.interfaces.dto.SuspenseItemDto;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * Manual bank reconciliation.
 *
 * <p>Posting endpoints are admin-only. A hand-written journal entry is the most powerful thing
 * anyone can do to this book — it is the one path that can move money between any two accounts on
 * a person's say-so — so it is narrower than the read side rather than the same.
 */
@Path("/recon/bank")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BankReconciliationResource {

    @Inject
    RecordBankTransactionUC recordTransaction;

    @Inject
    ReclassifySuspenseUC reclassifySuspense;

    @Inject
    AttestBankBalanceUC attestBalance;

    @Inject
    BankReconciliationQuery query;

    /** Books a bank movement that did not come from a payment. Suspense if unclassified. */
    @POST
    @Path("/transactions")
    @RolesAllowed("admin")
    public ApiResp<Map<String, Object>> record(RecordBankTransactionRequest request) {
        JournalEntry entry = recordTransaction.record(new BankTransaction(
                request.bankAccountCode(), request.amount(), request.counterAccountCode(),
                request.bankRef(), request.valueDate(), request.memo()));
        return ApiResp.fromData(Map.of(
                "entryUuid", entry.getUuid(),
                "bankRef", request.bankRef(),
                "classified", request.counterAccountCode() != null));
    }

    /** Names what a suspense item was, moving it to its real account. */
    @POST
    @Path("/reclassify")
    @RolesAllowed("admin")
    public ApiResp<Map<String, Object>> reclassify(ReclassifyRequest request) {
        JournalEntry entry = reclassifySuspense.reclassify(
                request.bankRef(), request.counterAccountCode(), request.amount(), request.memo());
        return ApiResp.fromData(Map.of(
                "entryUuid", entry.getUuid(),
                "bankRef", request.bankRef(),
                "counterAccountCode", request.counterAccountCode()));
    }

    /** Records the bank's own closing balance for a date. Posts nothing; it is evidence, not a movement. */
    @POST
    @Path("/attestations")
    @RolesAllowed("admin")
    public ApiResp<Map<String, Object>> attest(AttestBalanceRequest request) {
        BankStatementAttestation attestation = attestBalance.attest(
                request.bankAccountCode(), request.statementDate(),
                request.closingBalance(), request.statementRef(), request.note());
        return ApiResp.fromData(Map.of(
                "attestationUuid", attestation.getUuid(),
                "statementDate", attestation.getStatementDate().toString()));
    }

    /**
     * Every attestation with the gap between the bank's number and ours. Pass
     * {@code unreconciledOnly=true} for the working list.
     */
    @GET
    @Path("/attestations")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public ApiResp<List<BankAttestationDto>> attestations(@QueryParam("accountCode") String accountCode,
                                                          @QueryParam("unreconciledOnly") boolean unreconciledOnly) {
        return ApiResp.fromList(unreconciledOnly
                ? query.unreconciled(accountCode)
                : query.attestations(accountCode));
    }

    /** What is sitting in suspense, oldest first. This list should be short and should drain. */
    @GET
    @Path("/suspense")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public ApiResp<List<SuspenseItemDto>> suspense() {
        return ApiResp.fromList(query.suspense());
    }
}

