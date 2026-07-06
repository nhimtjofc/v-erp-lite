package base.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class WalletService {
    
    // Stub for Dev 2
    public void releaseEscrow(UUID escrowId) {
        throw new UnsupportedOperationException("TODO (Dev 2): Implement releaseEscrow logic. Remember to use LockModeType.PESSIMISTIC_WRITE on Wallet.");
    }
    
    // Stub for Dev 2
    public void creditWalletPending(UUID walletId, long amount, String txnType, String refType, UUID refId) {
        throw new UnsupportedOperationException("TODO (Dev 2): Implement creditWalletPending logic");
    }

    // Stub for Dev 2
    public void debitWalletAvailable(UUID walletId, long amount, String txnType, String refType, UUID refId) {
        throw new UnsupportedOperationException("TODO (Dev 2): Implement debitWalletAvailable logic");
    }
}
