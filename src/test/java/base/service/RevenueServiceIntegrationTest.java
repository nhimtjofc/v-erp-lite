package base.service;

import base.entity.Order;
import base.entity.OrderHistory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RevenueServiceIntegrationTest {

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    RevenueService revenueService;

    @Test
    void dailyReportMatchesOrdersCurrentlyCompleted() {
        String tenantId = "revenue-" + UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder completedWithHistory =
                testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(100_000));
        PaymentTestDataFactory.SeedOrder completedWithoutHistory =
                testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(50_000));
        PaymentTestDataFactory.SeedOrder noLongerCompleted =
                testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(70_000));

        QuarkusTransaction.requiringNew().run(() -> {
            markCompleted(completedWithHistory.order().getId(), true);
            markCompleted(completedWithoutHistory.order().getId(), false);
            markCompleted(noLongerCompleted.order().getId(), true);
            Order cancelled = Order.findById(noLongerCompleted.order().getId());
            cancelled.setStatus("CANCELLED");
        });

        LocalDate today = LocalDate.now(RevenueService.REPORTING_ZONE);
        RevenueService.DailyReport report = QuarkusTransaction.requiringNew().call(() ->
                revenueService.dailyReport(tenantId, null, today, today, "VND"));

        assertEquals(2, report.totals().orderCount());
        assertEquals(150_000, report.totals().netRevenueMinor());
        assertEquals(150_000, report.days().getFirst().netRevenueMinor());
    }

    private void markCompleted(UUID orderId, boolean addHistory) {
        Order order = Order.findById(orderId);
        order.setStatus("COMPLETED");
        order.setCurrency("VND");
        if (!addHistory) {
            return;
        }

        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setOrder(order);
        history.setTenantId(order.getTenantId());
        history.setOldStatus("DELIVERED");
        history.setNewStatus("COMPLETED");
        history.setCreatedAt(Instant.now());
        history.persist();
    }
}
