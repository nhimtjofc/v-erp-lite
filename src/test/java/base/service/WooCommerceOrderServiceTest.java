package base.service;

import base.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(WooCommerceOrderServiceTest.WooCommerceTestProfile.class)
class WooCommerceOrderServiceTest {

    @Inject
    WooCommerceOrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    private final List<String> createdTenants = new ArrayList<>();

    @AfterEach
    void cleanupData() {
        for (String tenantId : createdTenants) {
            QuarkusTransaction.requiringNew().run(() -> {
                WebhookEvent.delete("tenantId", tenantId);
                ExternalEntityMapping.delete("tenantId", tenantId);
                SalesChannel.delete("tenantId", tenantId);
                OrderItem.delete("tenantId", tenantId);
                OrderHistory.delete("tenantId", tenantId);
                Order.delete("tenantId", tenantId);
                CustomerVoucher.delete("tenantId", tenantId);
                Customer.delete("tenantId", tenantId);
                Voucher.delete("tenantId", tenantId);
                ProductVariant.delete("tenantId", tenantId);
                Product.delete("tenantId", tenantId);
                Business.delete("tenantId", tenantId);
                Tenant.delete("code", tenantId);
            });
        }
        createdTenants.clear();
    }

    private ChannelFixture createTestChannel(String suffix, int stockQty, long basePrice) {
        String tenantId = "woo-order-tst-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        UUID businessId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID productErpId = UUID.randomUUID();
        UUID variantErpId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            Tenant tenant = new Tenant();
            tenant.setId(UUID.randomUUID());
            tenant.setCode(tenantId);
            tenant.setName("Woo Order test tenant " + suffix);
            tenant.setActive(true);
            tenant.persist();

            Business business = new Business();
            business.setId(businessId);
            business.setTenantId(tenantId);
            business.setName("Woo Order test business " + suffix);
            business.setActive(true);
            business.persist();

            SalesChannel channel = new SalesChannel();
            channel.setId(channelId);
            channel.setTenantId(tenantId);
            channel.setBusinessId(businessId);
            channel.setStoreUrl("http://store-" + suffix + ".example");
            channel.setStatus("CONNECTED");
            channel.setHealthStatus("UP");
            channel.setSecretReference("test-channel");
            channel.setActive(true);
            channel.persist();

            Product product = new Product();
            product.setId(productErpId);
            product.setTenantId(tenantId);
            product.setBusiness(business);
            product.setSku("PROD-" + suffix);
            product.setName("Product " + suffix);
            product.setBasePriceMinor(basePrice);
            product.setStockQuantity(stockQty);
            product.setReservedQuantity(0);
            product.setActive(true);
            product.persist();

            ProductVariant variant = new ProductVariant();
            variant.setId(variantErpId);
            variant.setTenantId(tenantId);
            variant.setProduct(product);
            variant.setSku("VAR-" + suffix);
            variant.setPriceMinor(basePrice);
            variant.setStockQuantity(stockQty);
            variant.setReservedQuantity(0);
            variant.persist();

            // Setup mappings
            ExternalEntityMapping pm = new ExternalEntityMapping();
            pm.setId(UUID.randomUUID());
            pm.setTenantId(tenantId);
            pm.setSalesChannelId(channelId);
            pm.setEntityType("PRODUCT");
            pm.setErpEntityId(productErpId);
            pm.setExternalEntityId("ext-prod-" + suffix);
            pm.persist();

            ExternalEntityMapping vm = new ExternalEntityMapping();
            vm.setId(UUID.randomUUID());
            vm.setTenantId(tenantId);
            vm.setSalesChannelId(channelId);
            vm.setEntityType("PRODUCT_VARIANT");
            vm.setErpEntityId(variantErpId);
            vm.setExternalEntityId("ext-var-" + suffix);
            vm.persist();
        });

        createdTenants.add(tenantId);

        return new ChannelFixture(
                tenantId,
                businessId,
                channelId,
                productErpId,
                variantErpId,
                "ext-prod-" + suffix,
                "ext-var-" + suffix
        );
    }

    @Test
    void testExactlyOnceProcessing() throws Exception {
        ChannelFixture fixture = createTestChannel("idempotent", 5, 2000L);

        String orderId = "woo-123456";
        String jsonPayload = String.format(
                "{\"id\":\"%s\",\"billing\":{\"email\":\"user@example.com\",\"first_name\":\"John\",\"last_name\":\"Doe\"}," +
                "\"line_items\":[{\"product_id\":\"%s\",\"variation_id\":\"%s\",\"quantity\":2}]}",
                orderId, fixture.externalProductId(), fixture.externalVariantId()
        );
        JsonNode orderNode = objectMapper.readTree(jsonPayload);

        // Process first time
        Order firstOrder = QuarkusTransaction.requiringNew().call(() -> {
            WebhookEvent event = orderService.getOrCreateWebhookEvent(
                    fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, jsonPayload, "deliv-1"
            );
            Order o = orderService.processOrderWebhook(fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, orderNode, event);
            orderService.markWebhookEventSuccess(event.getId(), o.getId());
            return o;
        });

        assertNotNull(firstOrder);
        assertEquals(4000L, firstOrder.getSubtotalAmountMinor()); // 2000 * 2
        assertEquals("PENDING", firstOrder.getStatus());

        // Process second time (simulate duplicate webhook delivery)
        Order secondOrder = QuarkusTransaction.requiringNew().call(() -> {
            WebhookEvent event = orderService.getOrCreateWebhookEvent(
                    fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, jsonPayload, "deliv-1"
            );
            return orderService.processOrderWebhook(fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, orderNode, event);
        });

        // Must return the exact same order instance and not create a new DB record
        assertEquals(firstOrder.getId(), secondOrder.getId());

        // Verify reserved stock is only 2, not 4
        ProductVariant variant = ProductVariant.find("id = ?1 and tenantId = ?2", fixture.variantErpId(), fixture.tenantId()).firstResult();
        assertNotNull(variant);
        assertEquals(2, variant.getReservedQuantity());
    }

    @Test
    void testTenantIsolation() throws Exception {
        ChannelFixture tenantA = createTestChannel("tenantA", 10, 1000L);
        ChannelFixture tenantB = createTestChannel("tenantB", 10, 1000L);

        // Try to place an order in Tenant B but referencing Tenant A's external product mapping
        String orderId = "woo-789";
        String jsonPayload = String.format(
                "{\"id\":\"%s\",\"billing\":{\"email\":\"leak@example.com\",\"first_name\":\"A\",\"last_name\":\"B\"}," +
                "\"line_items\":[{\"product_id\":\"%s\",\"variation_id\":\"%s\",\"quantity\":1}]}",
                orderId, tenantA.externalProductId(), tenantA.externalVariantId() // Mappings from A
        );
        JsonNode orderNode = objectMapper.readTree(jsonPayload);

        // Process in Tenant B's context
        assertThrows(BadRequestException.class, () -> {
            QuarkusTransaction.requiringNew().run(() -> {
                WebhookEvent event = orderService.getOrCreateWebhookEvent(
                        tenantB.tenantId(), tenantB.channelId(), "woo-order-" + orderId, jsonPayload, "deliv-leak"
                );
                orderService.processOrderWebhook(tenantB.tenantId(), tenantB.channelId(), "woo-order-" + orderId, orderNode, event);
            });
        });
    }

    @Test
    void testCouponValidation() throws Exception {
        ChannelFixture fixture = createTestChannel("coupon", 10, 5000L); // Price $50 per unit

        // Seed a voucher inside a committed transaction
        String couponCode = "ERP50";
        QuarkusTransaction.requiringNew().run(() -> {
            Voucher voucher = new Voucher();
            voucher.setId(UUID.randomUUID());
            voucher.setTenantId(fixture.tenantId());
            voucher.setCode(couponCode);
            voucher.setName("50% Off Voucher");
            voucher.setDiscountType("PERCENTAGE");
            voucher.setDiscountValue(50L);
            voucher.setUsageLimit(100);
            voucher.setUsedCount(0);
            voucher.setMinOrderValueMinor(1000L);
            voucher.setMaxDiscountAmountMinor(10000L);
            voucher.setStartTime(Instant.now().minusSeconds(3600));
            voucher.setEndTime(Instant.now().plusSeconds(3600));
            voucher.setActive(true);
            voucher.persist();
        });

        // 3 items * $50 = $150 subtotal (15000 minor)
        // 50% discount on $150 = $75, capped at $100 max discount -> discount = $75 (7500 minor)
        // Order after discount = $75. Capped free shipping threshold is $100. Since $75 < $100, shipping is $10 (1000 minor)
        // VAT Tax = 10% of $75 = $7.50 -> rounded to 750 minor
        // Final Amount = $75 (7500) + $10 (1000) + $7.50 (750) = $92.50 (9250 minor)
        String orderId = "woo-coupon-1";
        String jsonPayload = String.format(
                "{\"id\":\"%s\",\"billing\":{\"email\":\"coupon@example.com\",\"first_name\":\"Jane\",\"last_name\":\"Doe\"}," +
                "\"line_items\":[{\"product_id\":\"%s\",\"variation_id\":\"%s\",\"quantity\":3}]," +
                "\"coupon_lines\":[{\"code\":\"%s\"}],\"total\":\"92.50\"}",
                orderId, fixture.externalProductId(), fixture.externalVariantId(), couponCode
        );
        JsonNode orderNode = objectMapper.readTree(jsonPayload);

        Order order = QuarkusTransaction.requiringNew().call(() -> {
            WebhookEvent event = orderService.getOrCreateWebhookEvent(
                    fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, jsonPayload, "deliv-coupon"
            );
            Order o = orderService.processOrderWebhook(fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, orderNode, event);
            orderService.markWebhookEventSuccess(event.getId(), o.getId());
            return o;
        });

        assertNotNull(order);
        assertEquals(15000L, order.getSubtotalAmountMinor());
        assertEquals(7500L, order.getDiscountAmountMinor());
        assertEquals(1000L, order.getShippingFeeMinor());
        assertEquals(750L, order.getTaxAmountMinor());
        assertEquals(9250L, order.getFinalAmountMinor());
        assertNull(order.getNotes()); // No price discrepancy because $92.50 matches Woo total!
    }

    @Test
    void concurrentCheckoutCompetingForLastSKU() throws Exception {
        // Create channel with ONLY 1 unit of stock
        ChannelFixture fixture = createTestChannel("concurrent", 1, 1500L);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String orderId = "order-concurrent-" + i;
            futures.add(executor.submit(() -> {
                latch.await(10, TimeUnit.SECONDS);
                try {
                    String json = String.format(
                            "{\"id\":\"%s\",\"billing\":{\"email\":\"test@example.com\",\"first_name\":\"A\",\"last_name\":\"B\"}," +
                            "\"line_items\":[{\"product_id\":\"%s\",\"variation_id\":\"%s\",\"quantity\":1}]}",
                            orderId, fixture.externalProductId(), fixture.externalVariantId()
                    );
                    JsonNode orderNode = new ObjectMapper().readTree(json);

                    QuarkusTransaction.requiringNew().run(() -> {
                        WebhookEvent event = orderService.getOrCreateWebhookEvent(
                                fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, json, null
                        );
                        orderService.processOrderWebhook(fixture.tenantId(), fixture.channelId(), "woo-order-" + orderId, orderNode, event);
                        orderService.markWebhookEventSuccess(event.getId(), UUID.randomUUID());
                    });

                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            }));
        }

        latch.countDown();
        for (Future<Boolean> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly one concurrent checkout should succeed
        assertEquals(1, successCount.get(), "Only one checkout thread should succeed");
        assertEquals(threadCount - 1, failureCount.get(), "Other checkouts should fail due to out-of-stock");

        // Verify final reserved stock in DB is exactly 1
        ProductVariant variant = ProductVariant.find("id = ?1 and tenantId = ?2", fixture.variantErpId(), fixture.tenantId()).firstResult();
        assertNotNull(variant);
        assertEquals(1, variant.getReservedQuantity());
    }

    public static class WooCommerceTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.keycloak.admin-client.server-url", "http://127.0.0.1:1",
                    "woocommerce.webhook.secrets.test-channel", "test-secret-that-must-never-be-serialized",
                    "woocommerce.bootstrap.enabled", "false"
            );
        }
    }

    private record ChannelFixture(
            String tenantId,
            UUID businessId,
            UUID channelId,
            UUID productErpId,
            UUID variantErpId,
            String externalProductId,
            String externalVariantId
    ) {}
}
