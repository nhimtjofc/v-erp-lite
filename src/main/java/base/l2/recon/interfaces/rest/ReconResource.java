package base.l2.recon.interfaces.rest;

import base.l0.system.api.ApiResp;
import base.l2.recon.application.LedgerInvariantChecker;
import base.l2.recon.domain.InvariantBreach;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/recon")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator"})
public class ReconResource {

    @Inject
    LedgerInvariantChecker checker;

    /**
     * Runs every invariant now and reports what failed. Also runs on a schedule.
     *
     * @param deep re-derive every balance from the first line ever written, instead of folding
     *             forward from the last closed period. Slow, and grows with total history — this is
     *             the check that keeps the fast one honest, so it is worth having on demand as well
     *             as nightly.
     */
    @GET
    @Path("/invariants")
    public ApiResp<Map<String, Object>> invariants(@QueryParam("deep") boolean deep) {
        List<InvariantBreach> breaches = deep ? checker.runFullAudit() : checker.runAll();
        long critical = breaches.stream()
                .filter(b -> b.severity() == InvariantBreach.Severity.CRITICAL).count();
        return ApiResp.fromData(Map.of(
                "healthy", breaches.isEmpty(),
                "criticalCount", critical,
                "breachCount", breaches.size(),
                "breaches", breaches,
                "mode", deep ? "full" : "incremental"));
    }
}

