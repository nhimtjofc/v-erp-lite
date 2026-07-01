package base.rest;

import base.entity.Customer;
import base.entity.CustomerVoucher;
import base.entity.Voucher;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/vouchers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VoucherResource {

    @Inject
    CallerContext caller;
    private static final Logger LOG = Logger.getLogger(VoucherResource.class);

    @GET
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public List<Voucher> list(@QueryParam("search") String search) {
        String tenantCode = caller.tenantCode();
        if (search != null && !search.isBlank()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            return Voucher.find(
                    "tenantId = ?1 AND (LOWER(code) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(name) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\')",
                    tenantCode, escaped
            ).list();
        }
        return Voucher.list("tenantId", tenantCode);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Voucher get(@PathParam("id") UUID id) {
        Voucher voucher = Voucher.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (voucher == null) {
            throw new NotFoundException("Voucher not found");
        }
        return voucher;
    }

    @GET
    @Path("/validate")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response validateVoucher(@QueryParam("code") String code, @QueryParam("subtotal") BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Voucher code is required");
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Subtotal must be zero or greater");
        }

        String tenantCode = caller.tenantCode();
        Voucher voucher = Voucher.find("code = ?1 and tenantId = ?2", code.trim(), tenantCode).firstResult();

        Map<String, Object> result = new HashMap<>();
        if (voucher == null) {
            result.put("valid", false);
            result.put("discountAmount", BigDecimal.ZERO);
            result.put("message", "Voucher not found");
        } else if (!voucher.isValid(subtotal)) {
            result.put("valid", false);
            result.put("discountAmount", BigDecimal.ZERO);
            if (!voucher.isActive()) {
                result.put("message", "Voucher is inactive");
            } else if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                result.put("message", "Voucher usage limit has been reached");
            } else if (subtotal.compareTo(voucher.getMinOrderValue()) < 0) {
                result.put("message", "Order subtotal is less than the minimum required value of " + voucher.getMinOrderValue());
            } else {
                result.put("message", "Voucher is expired or not yet valid");
            }
        } else {
            BigDecimal discount = voucher.calculateDiscount(subtotal);
            result.put("valid", true);
            result.put("discountAmount", discount);
            result.put("voucherId", voucher.getId());
            result.put("message", "Voucher applied successfully");
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/my")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public List<Map<String, Object>> myVouchers(@QueryParam("customerId") UUID customerId) {
        String tenantCode = caller.tenantCode();
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", customerId, tenantCode).firstResult();
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        List<CustomerVoucher> cvs = CustomerVoucher.find("customerId = ?1 and isUsed = false and tenantId = ?2", customerId, tenantCode).list();
        return cvs.stream().map(cv -> {
            Voucher v = Voucher.findById(cv.getVoucherId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", cv.getId());
            m.put("voucherId", cv.getVoucherId());
            m.put("collectedAt", cv.getCollectedAt());
            m.put("isUsed", cv.isUsed());
            m.put("voucher", v);
            return m;
        }).toList();
    }

    @POST
    @Path("/{id}/collect")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    @Transactional
    public Response collect(@PathParam("id") UUID voucherId, @QueryParam("customerId") UUID customerId) {
        String tenantCode = caller.tenantCode();
        Voucher voucher = Voucher.find("id = ?1 and tenantId = ?2", voucherId, tenantCode).firstResult();
        if (voucher == null) {
            throw new NotFoundException("Voucher not found");
        }
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", customerId, tenantCode).firstResult();
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        CustomerVoucher existing = CustomerVoucher.find("customerId = ?1 and voucherId = ?2 and tenantId = ?3", customerId, voucherId, tenantCode).firstResult();
        if (existing != null) {
            if (existing.isUsed()) {
                existing.setUsed(false);
                existing.setCollectedAt(Instant.now());
                return Response.ok(existing).build();
            }
            return Response.ok(existing).build();
        }
        CustomerVoucher cv = new CustomerVoucher();
        cv.setId(UUID.randomUUID());
        cv.setCustomerId(customerId);
        cv.setVoucherId(voucherId);
        cv.setTenantId(tenantCode);
        cv.persist();
        return Response.ok(cv).build();
    }

    @POST
    @RolesAllowed("admin")
    @Transactional
    public Response create(Voucher voucher) {
        validate(voucher);
        // Check for duplicates
        Voucher existing = Voucher.find("code = ?1 and tenantId = ?2", voucher.getCode().trim(), caller.tenantCode()).firstResult();
        if (existing != null) {
            throw new BadRequestException("Voucher code already exists");
        }

        voucher.setId(UUID.randomUUID());
        voucher.setTenantId(caller.tenantCode());
        voucher.setCreatedBy(caller.userId());
        voucher.setCode(voucher.getCode().trim());
        voucher.persist();
        return Response.created(URI.create("/api/vouchers/" + voucher.getId())).entity(voucher).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Voucher update(@PathParam("id") UUID id, Voucher updated) {
        validate(updated);
        Voucher voucher = Voucher.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (voucher == null) {
            throw new NotFoundException("Voucher not found");
        }

        // Check for duplicate code if code changed
        if (!voucher.getCode().equalsIgnoreCase(updated.getCode().trim())) {
            Voucher existing = Voucher.find("code = ?1 and tenantId = ?2", updated.getCode().trim(), caller.tenantCode()).firstResult();
            if (existing != null) {
                throw new BadRequestException("Voucher code already exists");
            }
        }

        voucher.setCode(updated.getCode().trim());
        voucher.setName(updated.getName());
        voucher.setDiscountType(updated.getDiscountType());
        voucher.setDiscountValue(updated.getDiscountValue());
        voucher.setMinOrderValue(updated.getMinOrderValue());
        voucher.setMaxDiscountAmount(updated.getMaxDiscountAmount());
        voucher.setStartTime(updated.getStartTime());
        voucher.setEndTime(updated.getEndTime());
        voucher.setUsageLimit(updated.getUsageLimit());
        voucher.setActive(updated.isActive());
        voucher.setUpdatedBy(caller.userId());
        return voucher;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        long deleted = Voucher.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Voucher not found");
        }
        return Response.noContent().build();
    }

    private void validate(Voucher voucher) {
        if (voucher.getCode() == null || voucher.getCode().isBlank()) {
            throw new BadRequestException("Voucher code is required");
        }
        if (voucher.getName() == null || voucher.getName().isBlank()) {
            throw new BadRequestException("Voucher name is required");
        }
        if (voucher.getDiscountType() == null || (!"PERCENTAGE".equalsIgnoreCase(voucher.getDiscountType()) && !"FIXED_AMOUNT".equalsIgnoreCase(voucher.getDiscountType()))) {
            throw new BadRequestException("Valid discount type (PERCENTAGE or FIXED_AMOUNT) is required");
        }
        if (voucher.getDiscountValue() == null || voucher.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Discount value must be greater than zero");
        }
        if (voucher.getMinOrderValue() == null || voucher.getMinOrderValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Minimum order value must be zero or greater");
        }
        if (voucher.getStartTime() != null && voucher.getEndTime() != null && voucher.getEndTime().isBefore(voucher.getStartTime())) {
            throw new BadRequestException("Voucher end time must be after start time");
        }
    }
}
