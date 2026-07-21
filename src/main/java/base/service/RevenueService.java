package base.service;

import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.PaymentRefund;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class RevenueService {

    public static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_REPORT_DAYS = 366;

    public DailyReport dailyReport(String tenantId, UUID businessId, LocalDate from, LocalDate to, String currency) {
        validateDateRange(from, to);
        String normalizedCurrency = normalizeCurrency(currency);
        Instant fromInstant = from.atStartOfDay(REPORTING_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(REPORTING_ZONE).toInstant();

        List<SaleEvent> sales = completedSales(
                tenantId,
                businessId,
                normalizedCurrency,
                fromInstant,
                toExclusive
        );
        List<RefundEvent> refunds = completedRefunds(
                tenantId,
                businessId,
                normalizedCurrency,
                fromInstant,
                toExclusive
        );

        return aggregate(from, to, normalizedCurrency, sales, refunds);
    }

    static DailyReport aggregate(LocalDate from, LocalDate to, String currency,
                                 List<SaleEvent> sales, List<RefundEvent> refunds) {
        Map<LocalDate, MutableDay> days = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.put(date, new MutableDay(date));
        }

        for (SaleEvent sale : sales) {
            MutableDay day = days.get(sale.occurredAt().atZone(REPORTING_ZONE).toLocalDate());
            if (day != null) {
                day.grossRevenueMinor += sale.amountMinor();
                day.orderCount++;
            }
        }
        for (RefundEvent refund : refunds) {
            MutableDay day = days.get(refund.occurredAt().atZone(REPORTING_ZONE).toLocalDate());
            if (day != null) {
                day.refundMinor += refund.amountMinor();
                day.refundCount++;
            }
        }

        List<DailyRevenue> daily = days.values().stream().map(MutableDay::toRevenue).toList();
        long grossRevenueMinor = daily.stream().mapToLong(DailyRevenue::grossRevenueMinor).sum();
        long refundMinor = daily.stream().mapToLong(DailyRevenue::refundMinor).sum();
        long orderCount = daily.stream().mapToLong(DailyRevenue::orderCount).sum();
        long refundCount = daily.stream().mapToLong(DailyRevenue::refundCount).sum();

        return new DailyReport(
                from,
                to,
                REPORTING_ZONE.getId(),
                currency,
                new RevenueTotals(
                        grossRevenueMinor,
                        refundMinor,
                        grossRevenueMinor - refundMinor,
                        orderCount,
                        refundCount
                ),
                daily
        );
    }

    private List<SaleEvent> completedSales(String tenantId, UUID businessId, String currency,
                                           Instant from, Instant toExclusive) {
        StringBuilder historyQuery = new StringBuilder(
                "tenantId = :tenantId and newStatus = :completed and createdAt >= :from "
                        + "and createdAt < :to and order.deletedAt is null and order.currency = :currency"
        );
        Map<String, Object> historyParams = new LinkedHashMap<>();
        historyParams.put("tenantId", tenantId);
        historyParams.put("completed", "COMPLETED");
        historyParams.put("from", from);
        historyParams.put("to", toExclusive);
        historyParams.put("currency", currency);
        if (businessId != null) {
            historyQuery.append(" and order.business.id = :businessId");
            historyParams.put("businessId", businessId);
        }

        List<OrderHistory> completionEvents = OrderHistory.list(historyQuery.toString(), historyParams);
        List<SaleEvent> sales = completionEvents.stream()
                .map(event -> new SaleEvent(
                        event.getOrder().getId(),
                        event.getCreatedAt(),
                        event.getOrder().getFinalAmountMinor()
                ))
                .collect(Collectors.toCollection(ArrayList::new));

        // Older imported orders may already be completed without an order-history row.
        // Count those on their order date, while avoiding duplicates for normal orders.
        StringBuilder legacyQuery = new StringBuilder(
                "tenantId = :tenantId and status = :completed and deletedAt is null "
                        + "and orderDate >= :from and orderDate < :to and currency = :currency"
        );
        Map<String, Object> legacyParams = new LinkedHashMap<>(historyParams);
        if (businessId != null) {
            legacyQuery.append(" and business.id = :businessId");
        }
        List<Order> legacyCandidates = Order.list(legacyQuery.toString(), legacyParams);
        Set<UUID> candidateIds = legacyCandidates.stream().map(Order::getId).collect(Collectors.toSet());
        Set<UUID> ordersWithHistory = candidateIds.isEmpty()
                ? Set.of()
                : OrderHistory.<OrderHistory>list("tenantId = ?1 and newStatus = ?2 and order.id in ?3",
                        tenantId, "COMPLETED", candidateIds)
                        .stream()
                        .map(event -> event.getOrder().getId())
                        .collect(Collectors.toSet());
        for (Order order : legacyCandidates) {
            if (!ordersWithHistory.contains(order.getId())) {
                sales.add(new SaleEvent(order.getId(), order.getOrderDate(), order.getFinalAmountMinor()));
            }
        }

        return sales;
    }

    private List<RefundEvent> completedRefunds(String tenantId, UUID businessId, String currency,
                                               Instant from, Instant toExclusive) {
        List<PaymentRefund> candidates = PaymentRefund.list(
                "tenantId = ?1 and status = ?2 and currency = ?3 and createdAt >= ?4 and createdAt < ?5",
                tenantId,
                "COMPLETED",
                currency,
                from,
                toExclusive
        );
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<UUID> orderIds = candidates.stream().map(PaymentRefund::getOrderId).collect(Collectors.toSet());
        List<Order> orders = Order.list("tenantId = ?1 and id in ?2", tenantId, orderIds);
        Map<UUID, Order> ordersById = orders.stream().collect(Collectors.toMap(Order::getId, order -> order));

        List<RefundEvent> refunds = new ArrayList<>();
        for (PaymentRefund refund : candidates) {
            Order order = ordersById.get(refund.getOrderId());
            if (order == null || (businessId != null
                    && (order.getBusiness() == null || !businessId.equals(order.getBusiness().getId())))) {
                continue;
            }
            refunds.add(new RefundEvent(refund.getOrderId(), refund.getCreatedAt(), refund.getAmountMinor()));
        }
        return refunds;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("From date must be on or before to date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_REPORT_DAYS) {
            throw new BadRequestException("Revenue reports are limited to 366 days");
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "VND" : currency.trim().toUpperCase();
    }

    public record DailyReport(
            LocalDate from,
            LocalDate to,
            String timezone,
            String currency,
            RevenueTotals totals,
            List<DailyRevenue> days
    ) {}

    public record RevenueTotals(
            long grossRevenueMinor,
            long refundMinor,
            long netRevenueMinor,
            long orderCount,
            long refundCount
    ) {}

    public record DailyRevenue(
            LocalDate date,
            long grossRevenueMinor,
            long refundMinor,
            long netRevenueMinor,
            long orderCount,
            long refundCount
    ) {}

    record SaleEvent(UUID orderId, Instant occurredAt, long amountMinor) {}

    record RefundEvent(UUID orderId, Instant occurredAt, long amountMinor) {}

    private static class MutableDay {
        private final LocalDate date;
        private long grossRevenueMinor;
        private long refundMinor;
        private long orderCount;
        private long refundCount;

        private MutableDay(LocalDate date) {
            this.date = date;
        }

        private DailyRevenue toRevenue() {
            return new DailyRevenue(
                    date,
                    grossRevenueMinor,
                    refundMinor,
                    grossRevenueMinor - refundMinor,
                    orderCount,
                    refundCount
            );
        }
    }
}
