package base.rest.dto;

import java.time.Instant;

public record ProviderOperationResult(
    String state,
    String providerReference,
    String rawPayload,
    Instant occurredAt,
    boolean retryable
) {}
