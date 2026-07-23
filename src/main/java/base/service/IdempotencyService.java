package base.service;

import base.entity.PaymentIdempotency;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class IdempotencyService {

    @Transactional
    public PaymentIdempotency checkAndCreate(String tenantId, String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Vui lòng nhập khóa idempotency");
        }
        String key = idempotencyKey.trim();
        PaymentIdempotency existing = PaymentIdempotency.find(
                "tenantId = ?1 and idempotencyKey = ?2",
                tenantId, key
        ).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        
        java.time.Instant now = java.time.Instant.now();
        
        if (existing != null) {
            if ("PROCESSING".equals(existing.getStatus())) {
                if (existing.getLeaseExpiresAt() != null && existing.getLeaseExpiresAt().isBefore(now)) {
                    // Lease expired, we can take over
                    existing.setRequestFingerprint(requestFingerprint);
                    existing.setLeaseExpiresAt(now.plus(3, java.time.temporal.ChronoUnit.MINUTES));
                    return null; // Return null so the caller knows they can process it
                }
                throw new BadRequestException("Giao dịch đang được xử lý, vui lòng thử lại sau");
            }
            if ("FAILED".equals(existing.getStatus())) {
                // Allow retry for failed records
                existing.setStatus("PROCESSING");
                existing.setRequestFingerprint(requestFingerprint);
                existing.setLeaseExpiresAt(now.plus(3, java.time.temporal.ChronoUnit.MINUTES));
                return null;
            }
            // If SUCCESS, verify fingerprint
            if (requestFingerprint != null && !requestFingerprint.equals(existing.getRequestFingerprint())) {
                throw new jakarta.ws.rs.ClientErrorException("Idempotency key mismatch", 409);
            }
            return existing;
        }

        PaymentIdempotency record = new PaymentIdempotency();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setIdempotencyKey(key);
        record.setStatus("PROCESSING");
        record.setRequestFingerprint(requestFingerprint);
        record.setLeaseExpiresAt(now.plus(3, java.time.temporal.ChronoUnit.MINUTES));
        record.persist();
        return null;
    }

    @Transactional
    public PaymentIdempotency complete(String tenantId, String idempotencyKey, String status, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Vui lòng nhập khóa idempotency");
        }
        String key = idempotencyKey.trim();
        PaymentIdempotency record = PaymentIdempotency.find(
                "tenantId = ?1 and idempotencyKey = ?2",
                tenantId, key
        ).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        
        if (record == null) {
            throw new NotFoundException("Không tìm thấy bản ghi idempotency");
        }
        if (!"PROCESSING".equals(record.getStatus())) {
            return record;
        }
        if (!List.of("SUCCESS", "FAILED").contains(status)) {
            throw new BadRequestException("Trạng thái idempotency không hợp lệ");
        }
        record.setStatus(status);
        record.setResponseBody(responseBody);
        record.setLeaseExpiresAt(null);
        return record;
    }

    public PaymentIdempotency getResult(String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return findExisting(tenantId, idempotencyKey.trim());
    }

    private PaymentIdempotency findExisting(String tenantId, String key) {
        return PaymentIdempotency.find(
                "tenantId = ?1 and idempotencyKey = ?2",
                tenantId, key
        ).firstResult();
    }
}
