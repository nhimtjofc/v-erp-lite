package base.rest;

import base.entity.Order;
import base.entity.Product;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Path("/chatbot")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatbotResource {

    @ConfigProperty(name = "chatbot.api-key")
    String apiKey;

    private void validateApiKey(String headerKey) {
        if (headerKey == null || !headerKey.equals(apiKey)) {
            throw new ForbiddenException("Invalid or missing Chatbot API Key");
        }
    }

    @GET
    @Path("/stats")
    public Map<String, Long> getStats(
            @HeaderParam("X-Chatbot-Key") String headerKey,
            @HeaderParam("X-Tenant-Code") String tenantCode) {
        validateApiKey(headerKey);
        
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BadRequestException("X-Tenant-Code header is required");
        }
        
        long count = Product.count("tenantId", tenantCode);
        Map<String, Long> response = new HashMap<>();
        response.put("totalProducts", count);
        return response;
    }

    @GET
    @Path("/orders/{orderNumber}")
    public OrderStatusDto getOrderStatus(
            @HeaderParam("X-Chatbot-Key") String headerKey,
            @HeaderParam("X-Tenant-Code") String tenantCode,
            @PathParam("orderNumber") String orderNumber) {
        validateApiKey(headerKey);
        
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BadRequestException("X-Tenant-Code header is required");
        }

        // Find order by orderNumber. 
        // Using orderNumber directly as that's what customers usually have.
        Order order = Order.find("orderNumber = ?1 and tenantId = ?2", orderNumber, tenantCode).firstResult();
        
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        // Map to a strict DTO to avoid leaking PII (shippingName, shippingPhone, shippingAddress, etc.)
        OrderStatusDto dto = new OrderStatusDto();
        dto.orderNumber = order.getOrderNumber();
        dto.status = order.getStatus();
        dto.finalAmountMinor = order.getFinalAmountMinor();
        dto.orderDate = order.getOrderDate();
        dto.currency = order.getCurrency();

        return dto;
    }

    public static class OrderStatusDto {
        public String orderNumber;
        public String status;
        public long finalAmountMinor;
        public Instant orderDate;
        public String currency;
    }
}
