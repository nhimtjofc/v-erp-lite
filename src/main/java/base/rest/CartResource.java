package base.rest;

import base.entity.*;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/carts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class CartResource {

    @Inject
    CallerContext caller;

    @GET
    public List<Cart> listCarts(
            @QueryParam("customerId") UUID customerId,
            @QueryParam("status") String status) {
        String tenantCode = caller.tenantCode();
        StringBuilder query = new StringBuilder("FROM Cart c WHERE c.tenantId = :tenantId");
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantCode);
        if (customerId != null) {
            query.append(" AND c.customer.id = :customerId");
            params.put("customerId", customerId);
        }
        if (status != null && !status.isBlank()) {
            query.append(" AND c.status = :status");
            params.put("status", status);
        }
        query.append(" ORDER BY c.updatedAt DESC");
        return Cart.find(query.toString(), params).list();
    }

    @GET
    @Path("/active/{customerId}")
    @Transactional
    public Cart getOrCreateActive(@PathParam("customerId") UUID customerId) {
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", customerId, caller.tenantCode()).firstResult();
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        Cart cart = Cart.find("customer.id = ?1 AND tenantId = ?2 AND status = 'ACTIVE'", customerId, caller.tenantCode()).firstResult();
        if (cart != null) return cart;
        cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setCustomer(customer);
        cart.setTenantId(caller.tenantCode());
        cart.setCreatedBy(caller.userId());
        cart.setStatus("ACTIVE");
        cart.persist();
        return cart;
    }

    @GET
    @Path("/{id}")
    public Cart getCart(@PathParam("id") UUID id) {
        Cart cart = Cart.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (cart == null) {
            throw new NotFoundException("Cart not found");
        }
        return cart;
    }

    @POST
    @Transactional
    public Response createCart(Cart cart) {
        if (cart.getCustomer() == null || cart.getCustomer().getId() == null) {
            throw new BadRequestException("Customer is required");
        }
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", cart.getCustomer().getId(), caller.tenantCode()).firstResult();
        if (customer == null) {
            throw new BadRequestException("Customer not found");
        }
        cart.setId(UUID.randomUUID());
        cart.setCustomer(customer);
        cart.setTenantId(caller.tenantCode());
        cart.setCreatedBy(caller.userId());
        cart.setStatus("ACTIVE");
        cart.persist();
        return Response.created(URI.create("/api/carts/" + cart.getId())).entity(cart).build();
    }

    @POST
    @Path("/{id}/items")
    @Transactional
    public Cart addItem(@PathParam("id") UUID cartId, AddItemRequest request) {
        Cart cart = Cart.find("id = ?1 and tenantId = ?2", cartId, caller.tenantCode()).firstResult();
        if (cart == null) {
            throw new NotFoundException("Cart not found");
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BadRequestException("Cannot modify a cart that is not ACTIVE");
        }
        if (request.productId == null) {
            throw new BadRequestException("Product ID is required");
        }

        Product product = Product.find("id = ?1 and tenantId = ?2", request.productId, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new BadRequestException("Product not found");
        }

        ProductVariant variant = null;
        if (request.variantId != null) {
            variant = ProductVariant.find("id = ?1 and tenantId = ?2", request.variantId, caller.tenantCode()).firstResult();
            if (variant == null) {
                throw new BadRequestException("Product variant not found");
            }
        }

        long price = resolvePrice(product, variant);

        CartItem existingItem = null;
        for (CartItem item : cart.getItems()) {
            boolean sameProduct = item.getProduct().getId().equals(request.productId);
            boolean sameVariant = (request.variantId == null && item.getVariant() == null)
                    || (request.variantId != null && item.getVariant() != null && item.getVariant().getId().equals(request.variantId));
            if (sameProduct && sameVariant) {
                existingItem = item;
                break;
            }
        }

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + (request.quantity > 0 ? request.quantity : 1));
        } else {
            CartItem item = new CartItem();
            item.setId(UUID.randomUUID());
            item.setCart(cart);
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(request.quantity > 0 ? request.quantity : 1);
            item.setUnitPriceMinor(price);
            item.setTenantId(caller.tenantCode());
            cart.getItems().add(item);
        }

        cart.setUpdatedBy(caller.userId());
        return cart;
    }

    @PUT
    @Path("/{cartId}/items/{itemId}")
    @Transactional
    public Cart updateItem(@PathParam("cartId") UUID cartId, @PathParam("itemId") UUID itemId, UpdateItemRequest request) {
        Cart cart = Cart.find("id = ?1 and tenantId = ?2", cartId, caller.tenantCode()).firstResult();
        if (cart == null) {
            throw new NotFoundException("Cart not found");
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BadRequestException("Cannot modify a cart that is not ACTIVE");
        }

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        if (request.quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(request.quantity);
        }
        cart.setUpdatedBy(caller.userId());
        return cart;
    }

    @DELETE
    @Path("/{cartId}/items/{itemId}")
    @Transactional
    public Cart removeItem(@PathParam("cartId") UUID cartId, @PathParam("itemId") UUID itemId) {
        Cart cart = Cart.find("id = ?1 and tenantId = ?2", cartId, caller.tenantCode()).firstResult();
        if (cart == null) {
            throw new NotFoundException("Cart not found");
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BadRequestException("Cannot modify a cart that is not ACTIVE");
        }

        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw new NotFoundException("Cart item not found");
        }
        cart.setUpdatedBy(caller.userId());
        return cart;
    }

    @POST
    @Path("/{id}/checkout")
    @Transactional
    public Order checkoutCart(@PathParam("id") UUID cartId, CheckoutRequest request) {
        Cart cart = Cart.find("id = ?1 and tenantId = ?2", cartId, caller.tenantCode()).firstResult();
        if (cart == null) {
            throw new NotFoundException("Cart not found");
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BadRequestException("Cart has already been checked out");
        }
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot checkout an empty cart");
        }
        if (cart.getCustomer() == null) {
            throw new BadRequestException("Cart has no customer");
        }

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomer(cart.getCustomer());
        order.setTenantId(caller.tenantCode());
        order.setCreatedBy(caller.userId());
        order.setStatus("PENDING_PAYMENT");

        order.setOrderNumber("ORD-" + System.currentTimeMillis());
        String name = cart.getCustomer().getFirstName() != null ? cart.getCustomer().getFirstName() : "";
        if (cart.getCustomer().getLastName() != null) {
            name += (name.isEmpty() ? "" : " ") + cart.getCustomer().getLastName();
        }
        if (name.isEmpty()) name = "Unknown Customer";
        
        order.setShippingName(name);
        order.setShippingPhone(cart.getCustomer().getPhone() != null ? cart.getCustomer().getPhone() : "N/A");
        order.setShippingAddress(cart.getCustomer().getAddress() != null ? cart.getCustomer().getAddress() : "N/A");

        long total = 0;
        Business orderBusiness = null;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (product.getBusiness() == null) {
                throw new BadRequestException("Every product in an order must belong to a business");
            }
            if (orderBusiness == null) {
                orderBusiness = product.getBusiness();
            } else if (!orderBusiness.getId().equals(product.getBusiness().getId())) {
                throw new BadRequestException("Multi-vendor order decisions are not supported; checkout one vendor at a time");
            }
            int qty = cartItem.getQuantity();

            ProductVariant variant = cartItem.getVariant();
            if (variant != null) {
                if (variant.getAvailableQuantity() < qty) {
                    throw new BadRequestException("Insufficient stock for variant: " + variant.getSku());
                }
                variant.setReservedQuantity(variant.getReservedQuantity() + qty);
                variant.setUpdatedBy(caller.userId());
            } else {
                if (product.getAvailableQuantity() < qty) {
                    throw new BadRequestException("Insufficient stock for product: " + product.getSku());
                }
                product.setReservedQuantity(product.getReservedQuantity() + qty);
                product.setUpdatedBy(caller.userId());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductVariant(variant);
            orderItem.setQuantity(qty);
            orderItem.setUnitPriceMinor(cartItem.getUnitPriceMinor());
            long subtotal = cartItem.getUnitPriceMinor() * qty;
            orderItem.setSubtotalAmountMinor(subtotal);
            orderItem.setTenantId(caller.tenantCode());
            order.getItems().add(orderItem);
            total += subtotal;
        }
        order.setBusiness(orderBusiness);

        order.setSubtotalAmountMinor(total);

        if (request == null) {
            request = new CheckoutRequest();
        }

        BigDecimal discountPct = request.discountPercent != null ? request.discountPercent : BigDecimal.ZERO;
        BigDecimal vatPct = request.vatPercent != null ? request.vatPercent : BigDecimal.ZERO;
        if (discountPct.compareTo(BigDecimal.ZERO) < 0 || discountPct.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Discount percent must be between 0 and 100");
        }
        if (vatPct.compareTo(BigDecimal.ZERO) < 0 || vatPct.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("VAT percent must be between 0 and 100");
        }

        long discountAmount = BigDecimal.valueOf(total).multiply(discountPct).divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP).longValue();
        long afterDiscount = total - discountAmount;
        long vatAmount = BigDecimal.valueOf(afterDiscount).multiply(vatPct).divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP).longValue();
        long shipping = request.shippingFeeMinor != null ? request.shippingFeeMinor : 0;

        order.setDiscountPercent(discountPct);
        order.setDiscountAmountMinor(discountAmount);
        order.setVatPercent(vatPct);
        order.setVatAmountMinor(vatAmount);
        order.setShippingFeeMinor(shipping);

        long finalAmount = afterDiscount + shipping + vatAmount;
        if (finalAmount < 0) finalAmount = 0;
        order.setFinalAmountMinor(finalAmount);

        if (request.notes != null) {
            order.setNotes(request.notes);
        }

        order.persist();

        cart.setStatus("CHECKED_OUT");
        cart.setUpdatedBy(caller.userId());

        return order;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteCart(@PathParam("id") UUID id) {
        long deleted = Cart.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Cart not found");
        }
        return Response.noContent().build();
    }

    private long resolvePrice(Product product, ProductVariant variant) {
        if (variant != null) return variant.getPriceMinor();
        if (product.getSalePriceMinor() != null) return product.getSalePriceMinor();
        return product.getBasePriceMinor();
    }

    public static class AddItemRequest {
        public UUID productId;
        public UUID variantId;
        public int quantity = 1;
    }

    public static class UpdateItemRequest {
        public int quantity;
    }

    public static class CheckoutRequest {
        public BigDecimal discountPercent;
        public BigDecimal vatPercent;
        public Long shippingFeeMinor;
        public String notes;
    }
}
