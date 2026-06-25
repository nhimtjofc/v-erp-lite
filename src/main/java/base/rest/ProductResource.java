package base.rest;

import base.entity.Category;
import base.entity.Product;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class ProductResource {

    @Inject
    CallerContext caller;

    @GET
    public List<Product> list(
            @QueryParam("search") String search,
            @QueryParam("categoryId") UUID categoryId
    ) {
        String tenantCode = caller.tenantCode();
        if (search != null && !search.isBlank() && categoryId != null) {
            String escaped = escapeLike(search);
            return Product.find(
                    "tenantId = ?1 AND category.id = ?2 AND (LOWER(name) LIKE LOWER(CONCAT('%',?3,'%')) ESCAPE '\\' OR LOWER(sku) LIKE LOWER(CONCAT('%',?3,'%')) ESCAPE '\\')",
                    tenantCode, categoryId, escaped
            ).list();
        }
        if (search != null && !search.isBlank()) {
            String escaped = escapeLike(search);
            return Product.find(
                    "tenantId = ?1 AND (LOWER(name) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(sku) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\')",
                    tenantCode, escaped
            ).list();
        }
        if (categoryId != null) {
            return Product.find("tenantId = ?1 AND category.id = ?2", tenantCode, categoryId).list();
        }
        return Product.list("tenantId", tenantCode);
    }

    @GET
    @Path("/{id}")
    public Product get(@PathParam("id") UUID id) {
        Product product = Product.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        return product;
    }

    @POST
    @Transactional
    public Response create(Product product) {
        validate(product);
        product.setId(UUID.randomUUID());
        product.setTenantId(caller.tenantCode());
        product.setCreatedBy(caller.userId());
        product.setCategory(resolveCategory(product));
        product.persist();
        return Response.created(URI.create("/api/products/" + product.getId())).entity(product).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Product update(@PathParam("id") UUID id, Product updated) {
        validate(updated);
        Product product = Product.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        product.setCategory(resolveCategory(updated));
        product.setSku(updated.getSku());
        product.setName(updated.getName());
        product.setDescription(updated.getDescription());
        product.setImageUrl(updated.getImageUrl());
        product.setBasePrice(updated.getBasePrice());
        product.setSalePrice(updated.getSalePrice());
        product.setSaleStartsAt(updated.getSaleStartsAt());
        product.setSaleEndsAt(updated.getSaleEndsAt());
        product.setActive(updated.isActive());
        product.setUpdatedBy(caller.userId());
        return product;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        long deleted = Product.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Product not found");
        }
        return Response.noContent().build();
    }

    private void validate(Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new BadRequestException("Product SKU is required");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BadRequestException("Product name is required");
        }
        if (product.getBasePrice() == null || product.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Base price must be zero or greater");
        }
        if (product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Sale price must be zero or greater");
        }
        if (product.getSaleStartsAt() != null && product.getSaleEndsAt() != null
                && product.getSaleEndsAt().isBefore(product.getSaleStartsAt())) {
            throw new BadRequestException("Sale end time must be after sale start time");
        }
    }

    private Category resolveCategory(Product product) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            return null;
        }
        Category category = Category.find(
                "id = ?1 and tenantId = ?2",
                product.getCategory().getId(), caller.tenantCode()
        ).firstResult();
        if (category == null) {
            throw new BadRequestException("Category not found");
        }
        return category;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
