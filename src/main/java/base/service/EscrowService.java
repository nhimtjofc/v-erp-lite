package base.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class EscrowService {

    @Inject
    WalletService walletService;

    // Stub for Dev 2
    public void releaseEscrow(UUID escrowId) {
        // Business logic for releasing escrow goes here
        // Then delegate to WalletService
        walletService.releaseEscrow(escrowId);
    }
}
