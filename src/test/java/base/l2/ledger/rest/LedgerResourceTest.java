package base.l2.ledger.rest;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The ledger's read endpoints, exercised over HTTP.
 *
 * <p>This layer had no tests at all, and that is where the one runtime failure this service has
 * shipped came from: {@code /statement} cast a driver value straight to {@code Timestamp} and threw
 * on every call, while the suite behind it stayed green. Query classes were covered; the thin layer
 * that serialises them was not, and serialisation is where the types actually meet the wire.
 *
 * <p>So these assert the envelope and the contract rather than the arithmetic — which paths exist,
 * what shape they answer in, and that the responses can be produced at all.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class LedgerResourceTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void accountsComeBackAsAPageNotABareArray() {
        fixture.bank("rest" + unique(), "EUR");

        given().when().get("/ledger/accounts?limit=5")
                .then().statusCode(200)
                .body("success", equalTo(true))
                .body("content.items", notNullValue())
                .body("content.limit", equalTo(5))
                .body("content.hasMore", notNullValue());
    }

    @Test
    void aLimitBeyondTheCeilingIsClampedRatherThanObeyed() {
        given().when().get("/ledger/accounts?limit=999999")
                .then().statusCode(200)
                .body("content.limit", equalTo(500));
    }

    @Test
    void aCursorWeDidNotIssueIsRejected() {
        given().when().get("/ledger/accounts?after=not-a-cursor")
                .then().statusCode(400)
                .body("error.code", equalTo("INVALID_CURSOR"));
    }

    @Test
    void searchFiltersReachTheQuery() {
        String name = "rest" + unique();
        fixture.bank(name, "EUR");

        given().when().get("/ledger/accounts?code=asset:bank:" + name + "&currency=EUR")
                .then().statusCode(200)
                .body("content.items.size()", equalTo(1))
                .body("content.items[0].code", equalTo("asset:bank:" + name))
                .body("content.hasMore", equalTo(false))
                .body("content.nextCursor", nullValue());
    }

    @Test
    void debtorsTakeAThreshold() {
        given().when().get("/ledger/accounts/debtors?minOwed=1000000000")
                .then().statusCode(200)
                .body("content.items.size()", equalTo(0));
    }

    /** The endpoint that used to throw. Every temporal column on the line crosses the wire here. */
    @Test
    void aStatementSerialisesItsDatesInsteadOfThrowing() {
        String name = "rest" + unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "rest:" + UUID.randomUUID())
                .effectiveAt(LocalDate.of(2028, 5, 4).atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                .line(PostingLine.debit(fixture.newId(), subject.getUuid(), Money.of(4_200, "EUR"))
                        .dueOn(LocalDate.of(2028, 6, 1)))
                .line(PostingLine.credit(fixture.newId(), other.getUuid(), Money.of(4_200, "EUR")))
                .build());

        given().when().get("/ledger/accounts/" + subject.getUuid() + "/statement")
                .then().statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].amountMinor", equalTo(4200))
                .body("content[0].effectiveAt", notNullValue())
                .body("content[0].dueDate", notNullValue());
    }

    @Test
    void balanceAsOfAnswersForADate() {
        String name = "rest" + unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "rest:" + UUID.randomUUID())
                .effectiveAt(LocalDate.of(2028, 5, 4).atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                .line(PostingLine.debit(fixture.newId(), subject.getUuid(), Money.of(900, "EUR")))
                .line(PostingLine.credit(fixture.newId(), other.getUuid(), Money.of(900, "EUR")))
                .build());

        given().when().get("/ledger/accounts/" + subject.getUuid() + "/balance-as-of?date=2028-12-31")
                .then().statusCode(200)
                .body("content.balanceMinor", equalTo(900))
                .body("content.asOf", equalTo("2028-12-31"));

        given().when().get("/ledger/accounts/" + subject.getUuid() + "/balance-as-of?date=2028-01-01")
                .then().statusCode(200)
                .body("content.balanceMinor", equalTo(0));
    }

    @Test
    void agingRenders() {
        given().when().get("/ledger/aging")
                .then().statusCode(200)
                .body("success", equalTo(true))
                .body("content.size()", greaterThanOrEqualTo(0));
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

