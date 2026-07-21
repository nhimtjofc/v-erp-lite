package base.service;

import base.entity.EscrowRecord;
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
            "COMMISSION_ADJUSTMENT",
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
                day.grossRevenueMinor += sale.amountMinor();
                day.commissionMinor += sale.commissionMinor();
                day.warehouseCostMinor += sale.warehouseCostMinor();
                day.orderCount++;
            }
        }
        for (RefundEvent refund : refunds) {
            MutableDay day = days.get(refund.occurredAt().atZone(REPORTING_ZONE).toLocalDate());
            if (day != null) {
                day.refundMinor += refund.amountMinor();
                day.commissionMinor -= refund.commissionReversalMinor();
                day.warehouseCostMinor -= refund.warehouseCostReversalMinor();
                day.refundCount++;
            }
        }

        List<DailyRevenue> daily = days.values().stream().map(MutableDay::toRevenue).toList();
        long grossRevenueMinor = daily.stream().mapToLong(DailyRevenue::grossRevenueMinor).sum();
        long refundMinor = daily.stream().mapToLong(DailyRevenue::refundMinor).sum();
        long commissionMinor = daily.stream().mapToLong(DailyRevenue::commissionMinor).sum();
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
                        grossRevenueMinor,
                        refundMinor,
                        grossRevenueMinor - refundMinor,
                        commissionMinor,
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
                operationalTotals.grossRevenueMinor(),
                operationalTotals.refundMinor(),
                operationalTotals.netRevenueMinor(),
                operationalTotals.commissionMinor(),
                operationalTotals.warehouseCostMinor(),
                adjustmentIncomeMinor,
                adjustmentExpenseMinor,
                profitMinor
        );

        List<BreakdownItem> breakdown = new ArrayList<>();
        breakdown.add(normalizedBreakdown("GROSS_REVENUE", "INCOME", operationalTotals.grossRevenueMinor()));
        breakdown.add(normalizedBreakdown("RETURNS", "EXPENSE", operationalTotals.refundMinor()));
        breakdown.add(normalizedBreakdown("COMMISSION", "EXPENSE", operationalTotals.commissionMinor()));
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
                .map(event -> saleEvent(event.getOrder(), event.getCreatedAt()))
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
                sales.add(saleEvent(order, order.getOrderDate()));
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
            boolean recognizedRevenue = OrderHistory.count(
                    "tenantId = ?1 and order.id = ?2 and newStatus = ?3",
                    tenantId,
                    order.getId(),
                    "COMPLETED"
            ) > 0;
            refunds.add(new RefundEvent(
                    refund.getOrderId(),
                    refund.getCreatedAt(),
                    refund.getAmountMinor(),
                    recognizedRevenue ? commissionFor(order) : 0,
                    recognizedRevenue ? inventoryCostService.orderCost(order) : 0
            ));
        }
        return refunds;
    }

    private SaleEvent saleEvent(Order order, Instant occurredAt) {
        return new SaleEvent(
                order.getId(),
                occurredAt,
                order.getFinalAmountMinor(),
                commissionFor(order),
                inventoryCostService.orderCost(order)
        );
    }

    private long commissionFor(Order order) {
        List<EscrowRecord> escrows = EscrowRecord.list(
                "tenantId = ?1 and orderId = ?2 and currency = ?3",
                order.getTenantId(),
                order.getId(),
                order.getCurrency()
        );
        if (escrows.isEmpty()) {
            return 0;
        }
        long sellerNetMinor = escrows.stream().mapToLong(EscrowRecord::getAmountMinor).sum();
        return Math.max(0, order.getFinalAmountMinor() - sellerNetMinor);
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
            long grossRevenueMinor,
            long refundMinor,
            long netRevenueMinor,
            long commissionMinor,
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
            long grossRevenueMinor,
            long refundMinor,
            long netRevenueMinor,
            long commissionMinor,
            long warehouseCostMinor,
            long profitMinor,
            long orderCount,
            long refundCount
    ) {}

    public record DailyRevenue(
            LocalDate date,
            long grossRevenueMinor,
            long refundMinor,
            long netRevenueMinor,
            long commissionMinor,
            long warehouseCostMinor,
            long profitMinor,
            long orderCount,
            long refundCount
    ) {}

    record SaleEvent(UUID orderId, Instant occurredAt, long amountMinor,
                     long commissionMinor, long warehouseCostMinor) {}

    record RefundEvent(UUID orderId, Instant occurredAt, long amountMinor,
                       long commissionReversalMinor, long warehouseCostReversalMinor) {}

    private static class MutableDay {
        private final LocalDate date;
        private long grossRevenueMinor;
        private long refundMinor;
        private long commissionMinor;
        private long warehouseCostMinor;
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
                    commissionMinor,
                    warehouseCostMinor,
                    grossRevenueMinor - refundMinor - commissionMinor - warehouseCostMinor,
                    orderCount,
                    refundCount
            );
        }
    }
}
