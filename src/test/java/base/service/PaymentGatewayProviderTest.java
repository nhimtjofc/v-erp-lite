package base.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayProviderTest {

    @Test
    void rejectsAnUnconfiguredProviderInsteadOfSilentlyUsingMock() {
        PaymentGatewayService gatewayService = new PaymentGatewayService();

        assertThrows(IllegalArgumentException.class, () -> gatewayService.generateTransactionId("unknown-provider"));
    }
}
