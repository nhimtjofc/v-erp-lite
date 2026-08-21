package base.service;

import base.entity.Business;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.PaymentRefund;
import base.entity.RevenueAdjustment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private static final Set<String> ADJUSTMENT_CATEGORIES = Set.of(
            "OTHER_INCOME",
            "OPERATING_EXPENSE",
            "WAREHOUSE_ADJUSTMENT",
            "RETURN_ADJUSTMENT"
    );

    @Inject
    InventoryCostService inventoryCostService;

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

    public MonthlyReport monthlyReport(String tenantId, UUID businessId, YearMonth month, String currency) {
        LocalDate today = LocalDate.now(REPORTING_ZONE);
        YearMonth currentMonth = YearMonth.from(today);
        if (month == null) {
            month = currentMonth;
        }
        if (month.isAfter(currentMonth)) {
            throw new BadRequestException("Month cannot be in the future");
        }

        LocalDate from = month.atDay(1);
        LocalDate to = month.equals(currentMonth) ? today : month.atEndOfMonth();
        String normalizedCurrency = normalizeCurrency(currency);
        DailyReport operations = dailyReport(tenantId, businessId, from, to, normalizedCurrency);
        List<AdjustmentEntry> adjustments = adjustmentEntries(
                tenantId,
                businessId,
                from,
                to,
                normalizedCurrency
        );
        return buildMonthlyReport(month.toString(), normalizedCurrency, operations, adjustments);
    }

    @Transactional
    public AdjustmentEntry createAdjustment(String tenantId, Business business, LocalDate entryDate,
                                            String category, String direction, long amountMinor,
                                            String currency, String description, UUID actorId) {
        if (entryDate == null) {
            throw new BadRequestException("Entry date is required");
        }
        if (entryDate.isAfter(LocalDate.now(REPORTING_ZONE))) {
            throw new BadRequestException("Entry date cannot be in the future");
        }
        String normalizedCategory = category == null ? "" : category.trim().toUpperCase();
        if (!ADJUSTMENT_CATEGORIES.contains(normalizedCategory)) {
            throw new BadRequestException("Unsupported revenue adjustment category");
        }
        String normalizedDirection = direction == null ? "" : direction.trim().toUpperCase();
        if (!Set.of("INCOME", "EXPENSE").contains(normalizedDirection)) {
            throw new BadRequestException("Direction must be INCOME or EXPENSE");
        }
        if (amountMinor <= 0) {
            throw new BadRequestException("Adjustment amount must be positive");
        }

        RevenueAdjustment adjustment = new RevenueAdjustment();
        adjustment.setId(UUID.randomUUID());
        adjustment.setTenantId(tenantId);
        adjustment.setBusiness(business);
        adjustment.setEntryDate(entryDate);
        adjustment.setCategory(normalizedCategory);
        adjustment.setDirection(normalizedDirection);
        adjustment.setAmountMinor(amountMinor);
        adjustment.setCurrency(normalizeCurrency(currency));
        adjustment.setDescription(description == null || description.isBlank() ? null : description.trim());
        adjustment.setCreatedBy(actorId);
        adjustment.persist();
        return AdjustmentEntry.from(adjustment);
    }

    @Transactional
    public void deleteAdjustment(String tenantId, UUID businessId, UUID adjustmentId) {
        String query = "id = :id and tenantId = :tenantId";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", adjustmentId);
        params.put("tenantId", tenantId);
        if (businessId != null) {
            query += " and business.id = :businessId";
            params.put("businessId", businessId);
        }
        RevenueAdjustment adjustment = RevenueAdjustment.find(query, params).firstResult();
        if (adjustment == null) {
            throw new NotFoundException("Revenue adjustment not found");
        }
        adjustment.delete();
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
                day.netRevenueMinor += sale.amountMinor();
                day.warehouseCostMinor += sale.warehouseCostMinor();
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
        long netRevenueMinor = daily.stream().mapToLong(DailyRevenue::netRevenueMinor).sum();
        long refundMinor = daily.stream().mapToLong(DailyRevenue::refundMinor).sum();
        long warehouseCostMinor = daily.stream().mapToLong(DailyRevenue::warehouseCostMinor).sum();
        long profitMinor = daily.stream().mapToLong(DailyRevenue::profitMinor).sum();
        long orderCount = daily.stream().mapToLong(DailyRevenue::orderCount).sum();
        long refundCount = daily.stream().mapToLong(DailyRevenue::refundCount).sum();

        return new DailyReport(
                from,
                to,
                REPORTING_ZONE.getId(),
                currency,
                new RevenueTotals(
                        netRevenueMinor,
                        refundMinor,
                        warehouseCostMinor,
                        profitMinor,
                        orderCount,
                        refundCount
                ),
                daily
        );
    }

    static MonthlyReport buildMonthlyReport(String month, String currency, DailyReport operations,
                                            List<AdjustmentEntry> adjustments) {
        long adjustmentIncomeMinor = adjustments.stream()
                .filter(entry -> "INCOME".equals(entry.direction()))
                .mapToLong(AdjustmentEntry::amountMinor)
                .sum();
        long adjustmentExpenseMinor = adjustments.stream()
                .filter(entry -> "EXPENSE".equals(entry.direction()))
                .mapToLong(AdjustmentEntry::amountMinor)
                .sum();

        RevenueTotals operationalTotals = operations.totals();
        long profitMinor = operationalTotals.profitMinor()
                + adjustmentIncomeMinor
                - adjustmentExpenseMinor;
        MonthlyTotals totals = new MonthlyTotals(
                operationalTotals.netRevenueMinor(),
                operationalTotals.refundMinor(),
                operationalTotals.warehouseCostMinor(),
                adjustmentIncomeMinor,
                adjustmentExpenseMinor,
                profitMinor
        );

        List<BreakdownItem> breakdown = new ArrayList<>();
        breakdown.add(normalizedBreakdown("NET_REVENUE", "INCOME", operationalTotals.netRevenueMinor()));
        breakdown.add(normalizedBreakdown("WAREHOUSE_COST", "EXPENSE", operationalTotals.warehouseCostMinor()));

        Map<String, Long> adjustmentBreakdown = new LinkedHashMap<>();
        for (AdjustmentEntry adjustment : adjustments) {
            String key = adjustment.category() + ":" + adjustment.direction();
            adjustmentBreakdown.merge(key, adjustment.amountMinor(), Long::sum);
        }
        for (Map.Entry<String, Long> entry : adjustmentBreakdown.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            breakdown.add(new BreakdownItem(parts[0], parts[1], entry.getValue()));
        }

        return new MonthlyReport(
                month,
                operations.from(),
                operations.to(),
                operations.timezone(),
                currency,
                totals,
                breakdown,
                adjustments
        );
    }

    private static BreakdownItem normalizedBreakdown(String code, String direction, long amountMinor) {
        if (amountMinor >= 0) {
            return new BreakdownItem(code, direction, amountMinor);
        }
        return new BreakdownItem(
                code,
                "INCOME".equals(direction) ? "EXPENSE" : "INCOME",
                Math.abs(amountMinor)
        );
    }

    private List<AdjustmentEntry> adjustmentEntries(String tenantId, UUID businessId, LocalDate from,
                                                    LocalDate to, String currency) {
        StringBuilder query = new StringBuilder(
                "tenantId = :tenantId and entryDate >= :from and entryDate <= :to and currency = :currency"
        );
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenantId);
        params.put("from", from);
        params.put("to", to);
        params.put("currency", currency);
        if (businessId != null) {
            query.append(" and business.id = :businessId");
            params.put("businessId", businessId);
        }
        query.append(" order by entryDate desc, createdAt desc");
        return RevenueAdjustment.<RevenueAdjustment>list(query.toString(), params)
                .stream()
                .map(AdjustmentEntry::from)
                .toList();
    }

    private List<SaleEvent> completedSales(String tenantId, UUID businessId, String currency,
                                           Instant from, Instant toExclusive) {
        StringBuilder orderQuery = new StringBuilder(
                "tenantId = :tenantId and status = :completed and deletedAt is null and currency = :currency"
        );
        Map<String, Object> orderParams = new LinkedHashMap<>();
        orderParams.put("tenantId", tenantId);
        orderParams.put("completed", "COMPLETED");
        orderParams.put("currency", currency);
        if (businessId != null) {
            orderQuery.append(" and business.id = :businessId");
            orderParams.put("businessId", businessId);
        }

        List<Order> completedOrders = Order.list(orderQuery.toString(), orderParams);
        if (completedOrders.isEmpty()) {
            return List.of();
        }

        Set<UUID> orderIds = completedOrders.stream().map(Order::getId).collect(Collectors.toSet());
        Map<UUID, Instant> completionTimes = new LinkedHashMap<>();
        for (OrderHistory event : OrderHistory.<OrderHistory>list(
                "tenantId = ?1 and newStatus = ?2 and order.id in ?3",
                tenantId, "COMPLETED", orderIds)) {
            completionTimes.merge(event.getOrder().getId(), event.getCreatedAt(),
                    (current, candidate) -> current.isAfter(candidate) ? current : candidate);
        }

        List<SaleEvent> sales = new ArrayList<>();
        for (Order order : completedOrders) {
            Instant completedAt = completionTimes.getOrDefault(order.getId(), order.getOrderDate());
            if (completedAt != null && !completedAt.isBefore(from) && completedAt.isBefore(toExclusive)) {
                sales.add(saleEvent(order, completedAt));
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
            refunds.add(new RefundEvent(
                    refund.getOrderId(),
                    refund.getCreatedAt(),
                    refund.getAmountMinor()
            ));
        }
        return refunds;
    }

    private SaleEvent saleEvent(Order order, Instant occurredAt) {
        return new SaleEvent(
                order.getId(),
                occurredAt,
                order.getFinalAmountMinor(),
                inventoryCostService.orderCost(order)
        );
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
        String normalized = currency == null || currency.isBlank() ? "VND" : currency.trim().toUpperCase();
        if (!"VND".equals(normalized)) {
            throw new BadRequestException("Revenue reports only support VND");
        }
        return normalized;
    }

    public record DailyReport(
            LocalDate from,
            LocalDate to,
            String timezone,
            String currency,
            RevenueTotals totals,
            List<DailyRevenue> days
    ) {}

    public record MonthlyReport(
            String month,
            LocalDate from,
            LocalDate to,
            String timezone,
            String currency,
            MonthlyTotals totals,
            List<BreakdownItem> breakdown,
            List<AdjustmentEntry> adjustments
    ) {}

    public record MonthlyTotals(
            long netRevenueMinor,
            long refundMinor,
            long warehouseCostMinor,
            long adjustmentIncomeMinor,
            long adjustmentExpenseMinor,
            long profitMinor
    ) {}

    public record BreakdownItem(
            String code,
            String direction,
            long amountMinor
    ) {}

    public record AdjustmentEntry(
            UUID id,
            UUID businessId,
            LocalDate entryDate,
            String category,
            String direction,
            long amountMinor,
            String currency,
            String description,
            Instant createdAt
    ) {
        static AdjustmentEntry from(RevenueAdjustment adjustment) {
            return new AdjustmentEntry(
                    adjustment.getId(),
                    adjustment.getBusiness() == null ? null : adjustment.getBusiness().getId(),
                    adjustment.getEntryDate(),
                    adjustment.getCategory(),
                    adjustment.getDirection(),
                    adjustment.getAmountMinor(),
                    adjustment.getCurrency(),
                    adjustment.getDescription(),
                    adjustment.getCreatedAt()
            );
        }
    }

    public record RevenueTotals(
            long netRevenueMinor,
            long refundMinor,
            long warehouseCostMinor,
            long profitMinor,
            long orderCount,
            long refundCount
    ) {}

    public record DailyRevenue(
            LocalDate date,
            long netRevenueMinor,
            long refundMinor,
            long warehouseCostMinor,
            long profitMinor,
            long orderCount,
            long refundCount
    ) {}

    record SaleEvent(UUID orderId, Instant occurredAt, long amountMinor, long warehouseCostMinor) {}

    record RefundEvent(UUID orderId, Instant occurredAt, long amountMinor) {}

    private static class MutableDay {
        private final LocalDate date;
        private long netRevenueMinor;
        private long refundMinor;
        private long warehouseCostMinor;
        private long orderCount;
        private long refundCount;

        private MutableDay(LocalDate date) {
            this.date = date;
        }

        private DailyRevenue toRevenue() {
            return new DailyRevenue(
                    date,
                    netRevenueMinor,
                    refundMinor,
                    warehouseCostMinor,
                    netRevenueMinor - warehouseCostMinor,
                    orderCount,
                    refundCount
            );
        }
    }
}
