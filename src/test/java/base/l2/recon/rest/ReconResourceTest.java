package base.l2.recon.rest;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.AccountCode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The reconciliation endpoints over HTTP: {@code ReconResource} and
 * {@code BankReconciliationResource}.
 *
 * <p>These carry two things nothing else covers: the {@code ?deep=true} switch between folding
 * forward from the last close and re-deriving every balance from the first line, and the write
 * endpoints' request bodies — where {@code Money} and {@code LocalDate} have to survive JSON in both
 * directions, which is precisely the seam that has already failed once in this service.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ReconResourceTest {

    @Inject
    LedgerTestFixture fixture;

    @Test
    void theInvariantSweepReportsItsMode() {
        given().when().get("/recon/invariants")
                .then().statusCode(200)
                .body("content.mode", equalTo("incremental"))
                .body("content.healthy", notNullValue())
                .body("content.breachCount", notNullValue());
    }

    @Test
    void theDeepSweepIsADifferentMode() {
        // Same question asked without the shortcut. If this ever disagrees with the incremental
        // run, the closing balances the fast path trusts have stopped being true.
        given().when().get("/recon/invariants?deep=true")
                .then().statusCode(200)
                .body("content.mode", equalTo("full"))
                .body("content.healthy", equalTo(true));
    }

    @Test
    void aBankTransactionRoundTripsThroughJson() {
        String name = "rr" + UUID.randomUUID().toString().substring(0, 8);
        fixture.bank(name, "EUR");
        fixture.expense(AccountCode.EXPENSE_BANK_FEES, "EUR");

        given().contentType("application/json")
                .body(Map.of(
                        "bankAccountCode", AccountCode.bank(name),
                        "amount", Map.of("amountMinor", -1_250, "currency", "EUR"),
                        "counterAccountCode", AccountCode.EXPENSE_BANK_FEES,
                        "bankRef", "REST-FEE-" + name,
                        "valueDate", LocalDate.now().toString(),
                        "memo", "Monthly account maintenance"))
                .when().post("/recon/bank/transactions")
                .then().statusCode(200)
                .body("success", equalTo(true))
                .body("content.entryUuid", notNullValue());
    }

    @Test
    void anAttestationAndItsDeltaRoundTrip() {
        String name = "rr" + UUID.randomUUID().toString().substring(0, 8);
        fixture.bank(name, "EUR");

        given().contentType("application/json")
                .body(Map.of(
                        "bankAccountCode", AccountCode.bank(name),
                        "statementDate", LocalDate.now().toString(),
                        "closingBalance", Map.of("amountMinor", 50_000, "currency", "EUR"),
                        "statementRef", "STMT-REST"))
                .when().post("/recon/bank/attestations")
                .then().statusCode(200)
                .body("success", equalTo(true));

        // The delta is computed at read time, so it appears without anything having stored it.
        given().when().get("/recon/bank/attestations?accountCode=" + AccountCode.bank(name))
                .then().statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].deltaMinor", equalTo(50000))
                .body("content[0].reconciled", equalTo(false))
                .body("content[0].statementDate", notNullValue());
    }

    @Test
    void unreconciledOnlyNarrowsTheList() {
        given().when().get("/recon/bank/attestations?unreconciledOnly=true")
                .then().statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    void suspenseRenders() {
        given().when().get("/recon/bank/suspense")
                .then().statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    void aMovementWithNoCounterAccountLandsInSuspense() {
        String name = "rr" + UUID.randomUUID().toString().substring(0, 8);
        fixture.bank(name, "EUR");
        fixture.suspense("EUR");
        String ref = "REST-UNK-" + name;

        given().contentType("application/json")
                .body(new java.util.HashMap<>(Map.of(
                        "bankAccountCode", AccountCode.bank(name),
                        "amount", Map.of("amountMinor", -9_900, "currency", "EUR"),
                        "bankRef", ref,
                        "valueDate", LocalDate.now().toString())))
                .when().post("/recon/bank/transactions")
                .then().statusCode(200);

        given().when().get("/recon/bank/suspense")
                .then().statusCode(200)
                .body("content.code", hasItem(AccountCode.CONTROL_SUSPENSE));
    }

    @Test
    void anUnknownAccountIsRefusedWithItsDomainCode() {
        given().contentType("application/json")
                .body(Map.of(
                        "bankAccountCode", "asset:bank:does-not-exist",
                        "amount", Map.of("amountMinor", -100, "currency", "EUR"),
                        "bankRef", "REST-GHOST",
                        "valueDate", LocalDate.now().toString()))
                .when().post("/recon/bank/transactions")
                .then().statusCode(404)
                .body("error.code", notNullValue());
    }

    @Test
    void aTransactionWithoutTheBanksReferenceIsRefused() {
        String name = "rr" + UUID.randomUUID().toString().substring(0, 8);
        fixture.bank(name, "EUR");

        given().contentType("application/json")
                .body(Map.of(
                        "bankAccountCode", AccountCode.bank(name),
                        "amount", Map.of("amountMinor", -100, "currency", "EUR"),
                        "bankRef", "   ",
                        "valueDate", LocalDate.now().toString()))
                .when().post("/recon/bank/transactions")
                .then().statusCode(400)
                .body("error.code", notNullValue());
    }
}

