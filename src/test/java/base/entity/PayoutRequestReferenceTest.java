package base.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PayoutRequestReferenceTest {

    @Test
    void exposesASeparateIssuedPaymentReference() throws Exception {
        Method getter = PayoutRequest.class.getMethod("getPaymentReference");
        Method setter = PayoutRequest.class.getMethod("setPaymentReference", String.class);
        PayoutRequest payout = new PayoutRequest();

        setter.invoke(payout, "P00ABCDEFGH1");

        assertNotNull(getter.invoke(payout));
    }
}
