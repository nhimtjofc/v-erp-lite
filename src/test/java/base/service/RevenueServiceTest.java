package base.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevenueServiceTest {

    @Test
    void aggregateBuildsEveryDayAndSubtractsRefunds() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 3);
        List<RevenueService.SaleEvent> sales = List.of(
                new RevenueService.SaleEvent(UUID.randomUUID(), instant("2026-07-01T02:00:00Z"), 500_000, 25_000, 300_000),
                new RevenueService.SaleEvent(UUID.randomUUID(), instant("2026-07-01T08:00:00Z"), 200_000, 10_000, 80_000),
                new RevenueService.SaleEvent(UUID.randomUUID(), instant("2026-07-03T02:00:00Z"), 300_000, 15_000, 120_000)
        );
        List<RevenueService.RefundEvent> refunds = List.of(
                new RevenueService.RefundEvent(UUID.randomUUID(), instant("2026-07-03T05:00:00Z"), 100_000, 5_000, 40_000)
        );

        RevenueService.DailyReport report = RevenueService.aggregate(from, to, "VND", sales, refunds);

        assertEquals(3, report.days().size());
        assertEquals(700_000, report.days().get(0).grossRevenueMinor());
        assertEquals(0, report.days().get(1).netRevenueMinor());
        assertEquals(200_000, report.days().get(2).netRevenueMinor());
        assertEquals(110_000, report.days().get(2).profitMinor());
        assertEquals(1_000_000, report.totals().grossRevenueMinor());
        assertEquals(100_000, report.totals().refundMinor());
        assertEquals(900_000, report.totals().netRevenueMinor());
        assertEquals(45_000, report.totals().commissionMinor());
        assertEquals(460_000, report.totals().warehouseCostMinor());
        assertEquals(395_000, report.totals().profitMinor());
        assertEquals(3, report.totals().orderCount());
        assertEquals(1, report.totals().refundCount());
    }

    private Instant instant(String value) {
        return Instant.parse(value);
    }
}
