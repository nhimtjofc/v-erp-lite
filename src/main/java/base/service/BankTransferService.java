package base.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ServiceUnavailableException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@ApplicationScoped
public class BankTransferService {

    @ConfigProperty(name = "payout.bank-transfer.mode", defaultValue = "mock")
    String mode;

    /**
     * Provider adapter seam. The idempotency key must be forwarded unchanged by
     * a production implementation. The lite mock returns the same bank reference
     * for every retry of the same payout.
     */
    public String transfer(String idempotencyKey, long amountMinor, String currency,
                           String bankName, String accountNumber, String accountHolder) {
        if (!"mock".equalsIgnoreCase(mode)) {
            throw new ServiceUnavailableException("No production bank transfer adapter is configured");
        }
        String payload = String.join(":", idempotencyKey, String.valueOf(amountMinor), currency,
                bankName, accountNumber, accountHolder);
        return "BANK_" + UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
    }
}
