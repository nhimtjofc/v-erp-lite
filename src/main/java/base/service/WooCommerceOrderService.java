package base.service;

import base.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class WooCommerceOrderService {

    private static final Logger log = LoggerFactory.getLogger(WooCommerceOrderService.class);

    private static final long FLAT_SHIPPING_FEE_MINOR = 1000;
    private static final long FREE_SHIPPING_THRESHOLD_MINOR = 10000;
    private static final double TAX_RATE = 0.10;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    WooCommerceSecretResolver secretResolver;

    public void verifyWebhookSignature(String rawBody, String signatureHeader, String tenantId, UUID salesChannelId) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Missing signature header X-WC-Webhook-Signature");
        }

        SalesChannel channel = SalesChannel.find("id = ?1 and tenantId = ?2", salesChannelId, tenantId).firstResult();
        if (channel == null) {
            throw new BadRequestException("Sales channel not found");
        }

        String secretReference = channel.getSecretReference();
        if (secretReference == null || secretReference.isBlank()) {
            throw new BadRequestException("Webhook secret not configured on sales channel");
        }

        Optional<String> secretOpt = secretResolver.resolveSecret(secretReference);
        if (secretOpt.isEmpty()) {
            throw new BadRequestException("Webhook secret could not be resolved from config");
        }

        String secret = secretOpt.get();
        if (!verifyHmacSha256(rawBody, signatureHeader, secret)) {
            throw new BadRequestException("HMAC signature verification failed");
        }
    }

    private boolean verifyHmacSha256(String data, String signature, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hmacBytes);
            return expected.equals(signature.trim());
        } catch (Exception e) {
            log.error("Failed to verify HMAC signature", e);
            return false;
        }
    }

    public WebhookEvent getOrCreateWebhookEvent(String tenantId, UUID salesChannelId, String externalEventId, String payload, String deliveryId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            WebhookEvent event = WebhookEvent.find(
                    "tenantId = ?1 and salesChannelId = ?2 and externalEventId = ?3",
                    tenantId, salesChannelId, externalEventId
            ).firstResult();

            if (event != null) {
                return event;
            }

            event = new WebhookEvent();
            event.setId(UUID.randomUUID());
            event.setTenantId(tenantId);
            event.setGateway("WOOCOMMERCE");
            event.setEventType("order.created");
            event.setSalesChannelId(salesChannelId);
            event.setExternalEventId(externalEventId);
            event.setRawPayload(payload);
            event.setStatus("PROCESSING");

            Map<String, Object> metadata = new HashMap<>();
            if (deliveryId != null) {
                metadata.put("delivery_id", deliveryId);
            }
            event.setChannelMetadata(metadata);

            event.persist();
            return event;
        });
    }

    public void markWebhookEventFailed(UUID eventId, String errorMessage) {
        QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent event = WebhookEvent.findById(eventId);
            if (event != null) {
                event.setStatus("FAILED");
                event.setErrorMessage(errorMessage);
                event.setProcessedAt(Instant.now());
            }
        });
    }

    public void markWebhookEventSuccess(UUID eventId, UUID orderId) {
        QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent event = WebhookEvent.findById(eventId);
            if (event != null) {
                event.setStatus("PROCESSED");
                event.setErrorMessage(null); // Clear errors on success
                event.setProcessedAt(Instant.now());
                Map<String, Object> metadata = event.getChannelMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
                metadata.put("order_id", orderId.toString());
                event.setChannelMetadata(metadata);
            }
        });
    }

    @Transactional
    public Order processOrderWebhook(String tenantId, UUID salesChannelId, String externalEventId, JsonNode orderNode, WebhookEvent event) {
        String wooOrderId = orderNode.path("id").asText();
        if (wooOrderId.isBlank()) {
            throw new BadRequestException("WooCommerce Order ID is missing");
        }

        ExternalEntityMapping orderMapping = ExternalEntityMapping.findByExternalId(
                tenantId, salesChannelId, "ORDER", wooOrderId
        );
        if (orderMapping != null) {
            Order existingOrder = Order.find("id = ?1 and tenantId = ?2", orderMapping.getErpEntityId(), tenantId).firstResult();
            if (existingOrder != null) {
                log.info("WooCommerce order {} already synced to ERP order {}", wooOrderId, existingOrder.getId());
                return existingOrder;
            }
        }

        // 1. Resolve Customer by Billing Email, or create a new one
        JsonNode billingNode = orderNode.path("billing");
        String email = billingNode.path("email").asText().trim();
        if (email.isBlank()) {
            throw new BadRequestException("Billing email is required");
        }

        Customer customer = Customer.find("email = ?1 and tenantId = ?2", email, tenantId).firstResult();
        if (customer == null) {
            customer = new Customer();
            customer.setId(UUID.randomUUID());
            customer.setTenantId(tenantId);
            customer.setFirstName(billingNode.path("first_name").asText());
            customer.setLastName(billingNode.path("last_name").asText());
            customer.setEmail(email);
            customer.setPhone(billingNode.path("phone").asText());

            // Build address
            String address_1 = billingNode.path("address_1").asText().trim();
            String address_2 = billingNode.path("address_2").asText().trim();
            String city = billingNode.path("city").asText().trim();
            String postcode = billingNode.path("postcode").asText().trim();
            String country = billingNode.path("country").asText().trim();
            String fullAddress = address_1 + (address_2.isEmpty() ? "" : ", " + address_2) + ", " + city + ", " + postcode + ", " + country;
            customer.setAddress(fullAddress);
            customer.setActive(true);
            customer.persist();
        }

        // 2. Resolve SalesChannel and Business
        SalesChannel channel = SalesChannel.find("id = ?1 and tenantId = ?2", salesChannelId, tenantId).firstResult();
        if (channel == null) {
            throw new BadRequestException("Sales channel not found");
        }
        Business business = Business.find("id = ?1 and tenantId = ?2", channel.getBusinessId(), tenantId).firstResult();
        if (business == null) {
            throw new BadRequestException("Business not found for sales channel");
        }

        // 3. Process line items and compute subtotal
        long subtotal = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenantId);
        order.setBusiness(business);
        order.setOrderDate(Instant.now());

        JsonNode lineItemsNode = orderNode.path("line_items");
        if (lineItemsNode.isMissingNode() || !lineItemsNode.isArray() || lineItemsNode.size() == 0) {
            throw new BadRequestException("Order must contain at least one line item");
        }

        for (JsonNode itemNode : lineItemsNode) {
            String externalProductId = itemNode.path("product_id").asText();
            String externalVariationId = itemNode.path("variation_id").asText();
            int quantity = itemNode.path("quantity").asInt();
            if (quantity <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }

            // Find ERP product mapping
            ExternalEntityMapping productMapping = ExternalEntityMapping.findByExternalId(
                    tenantId, salesChannelId, "PRODUCT", externalProductId
            );
            if (productMapping == null) {
                throw new BadRequestException("ERP Product mapping not found for WooCommerce product " + externalProductId);
            }

            // Acquire Pessimistic write lock on ERP product
            Product product = Product.find("id = ?1 and tenantId = ?2", productMapping.getErpEntityId(), tenantId)
                    .withLock(LockModeType.PESSIMISTIC_WRITE)
                    .firstResult();
            if (product == null) {
                throw new BadRequestException("ERP Product not found for mapping: " + productMapping.getErpEntityId());
            }

            ProductVariant variant = null;
            long unitPrice;

            if (externalVariationId != null && !"0".equals(externalVariationId) && !externalVariationId.isBlank()) {
                // Find ERP product variant mapping
                ExternalEntityMapping variantMapping = ExternalEntityMapping.findByExternalId(
                        tenantId, salesChannelId, "PRODUCT_VARIANT", externalVariationId
                );
                if (variantMapping == null) {
                    throw new BadRequestException("ERP Product variant mapping not found for WooCommerce variation " + externalVariationId);
                }

                // Acquire Pessimistic write lock on ERP variant
                variant = ProductVariant.find("id = ?1 and tenantId = ?2", variantMapping.getErpEntityId(), tenantId)
                        .withLock(LockModeType.PESSIMISTIC_WRITE)
                        .firstResult();
                if (variant == null) {
                    throw new BadRequestException("ERP Product variant not found for mapping: " + variantMapping.getErpEntityId());
                }

                if (variant.getAvailableQuantity() < quantity) {
                    throw new BadRequestException("Product variant " + variant.getSku() + " is out of stock (Available: " + variant.getAvailableQuantity() + ")");
                }

                unitPrice = variant.getPriceMinor();
                variant.setReservedQuantity(variant.getReservedQuantity() + quantity);
            } else {
                // simple product
                if (product.getAvailableQuantity() < quantity) {
                    throw new BadRequestException("Product " + product.getName() + " is out of stock (Available: " + product.getAvailableQuantity() + ")");
                }

                // Check sale price validity
                Instant now = Instant.now();
                if (product.getSalePriceMinor() != null && product.getSaleStartsAt() != null && product.getSaleEndsAt() != null
                        && now.isAfter(product.getSaleStartsAt()) && now.isBefore(product.getSaleEndsAt())) {
                    unitPrice = product.getSalePriceMinor();
                } else {
                    unitPrice = product.getBasePriceMinor();
                }

                product.setReservedQuantity(product.getReservedQuantity() + quantity);
            }

            long itemSubtotal = unitPrice * quantity;
            subtotal += itemSubtotal;

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setTenantId(tenantId);
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductVariant(variant);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPriceMinor(unitPrice);
            orderItem.setSubtotalAmountMinor(itemSubtotal);
            orderItems.add(orderItem);
        }

        // 4. Handle Voucher / Coupon Code
        long discount = 0;
        Voucher voucher = null;
        JsonNode couponLinesNode = orderNode.path("coupon_lines");
        if (couponLinesNode.isArray() && couponLinesNode.size() > 0) {
            String couponCode = couponLinesNode.get(0).path("code").asText().trim();
            if (!couponCode.isBlank()) {
                voucher = Voucher.find("code = ?1 and tenantId = ?2", couponCode, tenantId).firstResult();
                if (voucher == null) {
                    throw new BadRequestException("Invalid voucher code in ERP: " + couponCode);
                }
                if (!voucher.isValid(subtotal)) {
                    throw new BadRequestException("Voucher is not valid for this order in ERP: " + couponCode);
                }
                discount = voucher.calculateDiscount(subtotal);
                voucher.setUsedCount(voucher.getUsedCount() + 1);

                // Mark customer voucher as used if collected
                CustomerVoucher cv = CustomerVoucher.find("customerId = ?1 and voucherId = ?2 and tenantId = ?3 and isUsed = false",
                        customer.getId(), voucher.getId(), tenantId).firstResult();
                if (cv != null) {
                    cv.setUsed(true);
                }
            }
        }

        // 5. Compute Shipping Fee
        long orderAfterDiscount = subtotal - discount;
        long shippingFee = FLAT_SHIPPING_FEE_MINOR;
        if (orderAfterDiscount >= FREE_SHIPPING_THRESHOLD_MINOR) {
            shippingFee = 0;
        }

        // 6. Compute VAT Tax (10%)
        long tax = Math.round((double) orderAfterDiscount * TAX_RATE);

        // 7. Compute Final Amount
        long finalAmount = orderAfterDiscount + shippingFee + tax;

        // 8. Generate ERP Order Number
        String orderNumber = "WOO-" + wooOrderId + "-" + (int)(Math.random() * 900 + 100);

        // 9. Populate Order Details
        JsonNode shippingNode = orderNode.path("shipping");
        String shipFirstName = shippingNode.path("first_name").asText();
        String shipLastName = shippingNode.path("last_name").asText();
        String shipName = (shipFirstName + " " + shipLastName).trim();
        if (shipName.isEmpty()) {
            shipName = (billingNode.path("first_name").asText() + " " + billingNode.path("last_name").asText()).trim();
        }

        String shipPhone = billingNode.path("phone").asText();

        String shipAddress_1 = shippingNode.path("address_1").asText().trim();
        String shipAddress_2 = shippingNode.path("address_2").asText().trim();
        String shipCity = shippingNode.path("city").asText().trim();
        String shipPostcode = shippingNode.path("postcode").asText().trim();
        String shipCountry = shippingNode.path("country").asText().trim();
        String fullShipAddress = shipAddress_1 + (shipAddress_2.isEmpty() ? "" : ", " + shipAddress_2) + ", " + shipCity + ", " + shipPostcode + ", " + shipCountry;
        if (fullShipAddress.trim().equals(", , , ,")) {
            fullShipAddress = customer.getAddress();
        }

        // Upgrade: Price discrepancy check and warning log
        double wooTotalVal = orderNode.path("total").asDouble();
        long wooTotalMinor = Math.round(wooTotalVal * 100.0);
        String finalNotes = null;
        if (wooTotalMinor != finalAmount) {
            finalNotes = String.format(
                    "[System Warning] WooCommerce total ($%.2f) differs from ERP calculated total ($%.2f). Discrepancy might be due to tax/shipping configuration difference.",
                    wooTotalVal, (double) finalAmount / 100.0
            );
        }

        order.setCustomer(customer);
        order.setVoucher(voucher);
        order.setOrderNumber(orderNumber);
        order.setStatus("PENDING");
        order.setShippingName(shipName);
        order.setShippingPhone(shipPhone);
        order.setShippingAddress(fullShipAddress);
        order.setShippingNote(orderNode.path("customer_note").asText());
        order.setSubtotalAmountMinor(subtotal);
        order.setDiscountAmountMinor(discount);
        order.setShippingFeeMinor(shippingFee);
        order.setTaxAmountMinor(tax);
        order.setVatPercent(BigDecimal.valueOf(10));
        order.setVatAmountMinor(tax);
        order.setFinalAmountMinor(finalAmount);
        order.setNotes(finalNotes);
        order.setItems(orderItems);
        order.persist();

        // 10. Write initial history
        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setTenantId(tenantId);
        history.setOrder(order);
        history.setOldStatus(null);
        history.setNewStatus("PENDING");
        history.setNote("WooCommerce order created via integration webhook");
        history.persist();

        // 11. Record ExternalEntityMapping for the order
        ExternalEntityMapping mapping = new ExternalEntityMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setTenantId(tenantId);
        mapping.setSalesChannelId(salesChannelId);
        mapping.setEntityType("ORDER");
        mapping.setErpEntityId(order.getId());
        mapping.setExternalEntityId(wooOrderId);
        mapping.setSyncStatus("SYNCED");
        mapping.setLastSyncedAt(Instant.now());
        mapping.setLastAttemptedAt(Instant.now());

        Map<String, Object> mapMeta = new HashMap<>();
        mapMeta.put("order_number", orderNode.path("number").asText());
        mapping.setMetadata(mapMeta);
        mapping.persist();

        return order;
    }

    public Map<String, Object> getOrderStatus(String tenantId, UUID salesChannelId, String externalId) {
        ExternalEntityMapping mapping = ExternalEntityMapping.findByExternalId(
                tenantId, salesChannelId, "ORDER", externalId
        );
        if (mapping == null) {
            throw new jakarta.ws.rs.NotFoundException("Order mapping not found for external ID: " + externalId);
        }

        Order order = Order.find("id = ?1 and tenantId = ?2", mapping.getErpEntityId(), tenantId).firstResult();
        if (order == null) {
            throw new jakarta.ws.rs.NotFoundException("ERP order not found for mapping");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId().toString());
        result.put("orderNumber", order.getOrderNumber());
        result.put("status", order.getStatus());
        result.put("subtotalAmountMinor", order.getSubtotalAmountMinor());
        result.put("shippingFeeMinor", order.getShippingFeeMinor());
        result.put("taxAmountMinor", order.getTaxAmountMinor());
        result.put("finalAmountMinor", order.getFinalAmountMinor());
        result.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);

        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                String productName = item.getProduct() != null ? item.getProduct().getName() : "";
                itemMap.put("productName", productName);
                itemMap.put("sku", item.getProduct() != null ? item.getProduct().getSku() : "");
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("unitPriceMinor", item.getUnitPriceMinor());
                itemMap.put("subtotalAmountMinor", item.getSubtotalAmountMinor());
                items.add(itemMap);
            }
        }
        result.put("items", items);

        return result;
    }

    @Transactional
    public Order updateOrderWebhook(String tenantId, UUID salesChannelId, String externalEventId, JsonNode orderNode, WebhookEvent event) {
        String wooOrderId = orderNode.path("id").asText();
        if (wooOrderId.isBlank()) {
            throw new BadRequestException("WooCommerce Order ID is missing");
        }

        ExternalEntityMapping orderMapping = ExternalEntityMapping.findByExternalId(
                tenantId, salesChannelId, "ORDER", wooOrderId
        );
        if (orderMapping == null) {
            throw new BadRequestException("Order mapping not found in ERP for WooCommerce ID: " + wooOrderId);
        }

        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", orderMapping.getErpEntityId(), tenantId).firstResult();
        if (order == null) {
            throw new BadRequestException("Order not found in ERP: " + orderMapping.getErpEntityId());
        }

        String oldStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        String wooStatus = orderNode.path("status").asText().trim().toLowerCase();

        String newStatus;
        if ("cancelled".equals(wooStatus)) {
            newStatus = "CANCELLED";
        } else if ("completed".equals(wooStatus)) {
            newStatus = "COMPLETED";
        } else if ("processing".equals(wooStatus)) {
            newStatus = "CONFIRMED";
        } else if ("on-hold".equals(wooStatus)) {
            newStatus = "PENDING";
        } else {
            newStatus = oldStatus;
        }

        if (oldStatus.equals(newStatus)) {
            return order;
        }

        if (!isAllowedStatusTransition(oldStatus, newStatus)) {
            log.warn("Invalid WooCommerce status transition from {} to {} for order {}", oldStatus, newStatus, order.getId());
            return order;
        }

        // Stock and Reservation updates
        if (List.of("PENDING", "CONFIRMED", "SHIPPING").contains(oldStatus) && "CANCELLED".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if (item.getProductVariant() != null) {
                    ProductVariant variant = ProductVariant.find("id = ?1 and tenantId = ?2", item.getProductVariant().getId(), tenantId)
                            .withLock(LockModeType.PESSIMISTIC_WRITE)
                            .firstResult();
                    if (variant != null) {
                        variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
                    }
                } else {
                    Product product = Product.find("id = ?1 and tenantId = ?2", item.getProduct().getId(), tenantId)
                            .withLock(LockModeType.PESSIMISTIC_WRITE)
                            .firstResult();
                    if (product != null) {
                        product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
                    }
                }
            }
        } else if ("SHIPPING".equals(oldStatus) && "SHIPPED".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if (item.getProductVariant() != null) {
                    ProductVariant variant = ProductVariant.find("id = ?1 and tenantId = ?2", item.getProductVariant().getId(), tenantId)
                            .withLock(LockModeType.PESSIMISTIC_WRITE)
                            .firstResult();
                    if (variant != null) {
                        variant.setStockQuantity(Math.max(0, variant.getStockQuantity() - item.getQuantity()));
                        variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
                    }
                } else {
                    Product product = Product.find("id = ?1 and tenantId = ?2", item.getProduct().getId(), tenantId)
                            .withLock(LockModeType.PESSIMISTIC_WRITE)
                            .firstResult();
                    if (product != null) {
                        product.setStockQuantity(Math.max(0, product.getStockQuantity() - item.getQuantity()));
                        product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
                    }
                }
            }
        }

        order.setStatus(newStatus);

        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setTenantId(tenantId);
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote("WooCommerce order status updated to " + wooStatus + " via webhook");
        history.persist();

        return order;
    }

    private boolean isAllowedStatusTransition(String from, String to) {
        if (from.equals(to)) return true;
        switch (from) {
            case "PENDING":
                return List.of("CONFIRMED", "CANCELLED").contains(to);
            case "CONFIRMED":
                return List.of("SHIPPING", "CANCELLED").contains(to);
            case "SHIPPING":
                return List.of("SHIPPED", "CANCELLED").contains(to);
            case "SHIPPED":
                return "COMPLETED".equals(to);
            case "COMPLETED":
            case "CANCELLED":
            default:
                return false;
        }
    }
}
