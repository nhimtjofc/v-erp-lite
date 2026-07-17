package base.rest;

import java.time.Instant;
import java.util.UUID;

public class WooCommerceOrderDto {
    public String wooOrderId;
    public UUID erpOrderId;
    public String orderNumber;
    public String customerName;
    public Instant orderDate;
    public long finalAmountMinor;
    public String syncStatus; // SYNCED, FAILED, PROCESSING
    public String lastErrorMessage;
    public Instant lastAttemptedAt;

    public WooCommerceOrderDto() {}

    public WooCommerceOrderDto(
            String wooOrderId,
            UUID erpOrderId,
            String orderNumber,
            String customerName,
            Instant orderDate,
            long finalAmountMinor,
            String syncStatus,
            String lastErrorMessage,
            Instant lastAttemptedAt
    ) {
        this.wooOrderId = wooOrderId;
        this.erpOrderId = erpOrderId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.finalAmountMinor = finalAmountMinor;
        this.syncStatus = syncStatus;
        this.lastErrorMessage = lastErrorMessage;
        this.lastAttemptedAt = lastAttemptedAt;
    }
}
