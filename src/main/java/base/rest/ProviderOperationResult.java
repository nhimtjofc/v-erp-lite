package base.rest;

import java.util.Map;

public record ProviderOperationResult(
        String transactionId,
        String status,
        String message,
        Map<String, String> metadata
) {}
