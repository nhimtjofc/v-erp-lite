package base.rest;

import base.entity.Category;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.Tag;
import base.security.BusinessAccess;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    // Trigger hot reload for new sample vendor products
    @Inject
    CallerContext caller;

    @Inject
    BusinessAccess businessAccess;

    @GET
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public List<Product> listProducts(
            @QueryParam("search") String search,
            @QueryParam("categoryId") UUID categoryId,
            @QueryParam("tagId") UUID tagId) {
        String tenantCode = caller.tenantCode();
        StringBuilder query = new StringBuilder("SELECT DISTINCT p FROM Product p");
        if (tagId != null) {
            query.append(" JOIN p.tags t");
        }
        query.append(" WHERE p.tenantId = :tenantId");

        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantCode);

        if (businessAccess.isScopedStaff()) {
            var business = businessAccess.findCurrentBusiness();
            if (business != null) {
                query.append(" AND p.business.id = :businessId");
                params.put("businessId", business.getId());
            } else {
                query.append(" AND p.id IS NULL");
            }
        }

        if (categoryId != null) {
            query.append(" AND p.category.id = :categoryId");
            params.put("categoryId", categoryId);
        }

        if (tagId != null) {
            query.append(" AND t.id = :tagId");
            params.put("tagId", tagId);
        }

        if (search != null && !search.isBlank()) {
            String escaped = escapeLike(search);
            query.append(
                    " AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\' OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\')");
            params.put("search", escaped);
        }

        return Product.find(query.toString(), params).list();
    }

    @GET
    @Path("/{id}")
    public Product getProduct(@PathParam("id") UUID id) {
        Product product = Product.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        if (product.getBusiness() != null) {
            businessAccess.requireAccess(product.getBusiness().getId());
        } else if (businessAccess.isScopedStaff()) {
            throw new ForbiddenException("You are not allowed to view this product");
        }
        return product;
    }

    @POST
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Response createProduct(Product product) {
        validate(product);
        product.setId(UUID.randomUUID());
        product.setTenantId(caller.tenantCode());
        product.setCreatedBy(caller.userId());
        product.setCategory(resolveCategory(product));
        product.setTags(resolveTags(product));
        if (businessAccess.isScopedStaff()) {
            product.setBusiness(businessAccess.requireCurrentBusiness());
        }

        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                variant.setId(UUID.randomUUID());
                variant.setProduct(product);
                variant.setTenantId(caller.tenantCode());
                variant.setCreatedBy(caller.userId());
            }
        }

        product.persist();
        return Response.created(URI.create("/api/products/" + product.getId())).entity(product).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Product updateProduct(@PathParam("id") UUID id, Product updated) {
        validate(updated);
        Product product = Product.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        if (product.getBusiness() != null) {
            businessAccess.requireAccess(product.getBusiness().getId());
        } else if (businessAccess.isScopedStaff()) {
            throw new ForbiddenException("You are not allowed to modify this product");
        }
        product.setCategory(resolveCategory(updated));
        product.setTags(resolveTags(updated));
        product.setSku(updated.getSku());
        product.setName(updated.getName());
        product.setDescription(updated.getDescription());
        product.setImageUrl(updated.getImageUrl());
        product.setBasePriceMinor(updated.getBasePriceMinor());
        product.setSalePriceMinor(updated.getSalePriceMinor());
        product.setCostPriceMinor(updated.getCostPriceMinor());
        product.setSaleStartsAt(updated.getSaleStartsAt());
        product.setSaleEndsAt(updated.getSaleEndsAt());
        product.setActive(updated.isActive());
        product.setStockQuantity(updated.getStockQuantity());
        product.setLowStockThreshold(updated.getLowStockThreshold());
        product.setOptions(updated.getOptions());
        product.setUpdatedBy(caller.userId());

        // Sync variants safely
        Map<String, ProductVariant> existingVariants = new HashMap<>();
        if (product.getVariants() != null) {
            for (ProductVariant v : product.getVariants()) {
                existingVariants.put(v.getSku(), v);
            }
        }

        List<ProductVariant> newVariants = new ArrayList<>();
        if (updated.getVariants() != null) {
            for (ProductVariant updatedVar : updated.getVariants()) {
                ProductVariant var = existingVariants.get(updatedVar.getSku());
                if (var != null) {
                    var.setPriceMinor(updatedVar.getPriceMinor());
                    var.setCostPriceMinor(updatedVar.getCostPriceMinor());
                    var.setStockQuantity(updatedVar.getStockQuantity());
                    var.setLowStockThreshold(updatedVar.getLowStockThreshold());
                    var.setOptionValues(updatedVar.getOptionValues());
                    var.setUpdatedBy(caller.userId());
                    newVariants.add(var);
                } else {
                    ProductVariant newVar = new ProductVariant();
                    newVar.setId(UUID.randomUUID());
                    newVar.setProduct(product);
                    newVar.setSku(updatedVar.getSku());
                    newVar.setPriceMinor(updatedVar.getPriceMinor());
                    newVar.setCostPriceMinor(updatedVar.getCostPriceMinor());
                    newVar.setStockQuantity(updatedVar.getStockQuantity());
                    newVar.setLowStockThreshold(updatedVar.getLowStockThreshold());
                    newVar.setOptionValues(updatedVar.getOptionValues());
                    newVar.setTenantId(caller.tenantCode());
                    newVar.setCreatedBy(caller.userId());
                    newVariants.add(newVar);
                }
            }
        }

        if (product.getVariants() == null) {
            product.setVariants(new ArrayList<>());
        }
        product.getVariants().clear();
        product.getVariants().addAll(newVariants);

        return product;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Response deleteProduct(@PathParam("id") UUID id) {
        Product product = Product.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        if (product.getBusiness() != null) {
            businessAccess.requireAccess(product.getBusiness().getId());
        } else if (businessAccess.isScopedStaff()) {
            throw new ForbiddenException("You are not allowed to delete this product");
        }
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
        if (product.getBasePriceMinor() < 0) {
            throw new BadRequestException("Base price must be zero or greater");
        }
        if (product.getSalePriceMinor() != null && product.getSalePriceMinor() < 0) {
            throw new BadRequestException("Sale price must be zero or greater");
        }
        if (product.getCostPriceMinor() < 0) {
            throw new BadRequestException("Cost price must be zero or greater");
        }
        if (product.getSaleStartsAt() != null && product.getSaleEndsAt() != null
                && product.getSaleEndsAt().isBefore(product.getSaleStartsAt())) {
            throw new BadRequestException("Sale end time must be after sale start time");
        }

        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getSku() == null || variant.getSku().isBlank()) {
                    throw new BadRequestException("Variant SKU is required");
                }
                if (variant.getPriceMinor() < 0) {
                    throw new BadRequestException("Variant price must be zero or greater");
                }
                if (variant.getCostPriceMinor() != null && variant.getCostPriceMinor() < 0) {
                    throw new BadRequestException("Variant cost price must be zero or greater");
                }
                if (variant.getStockQuantity() < 0) {
                    throw new BadRequestException("Variant stock quantity must be zero or greater");
                }
            }
        }
    }

    private Category resolveCategory(Product product) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            return null;
        }
        Category category = Category.find(
                "id = ?1 and tenantId = ?2",
                product.getCategory().getId(), caller.tenantCode()).firstResult();
        if (category == null) {
            throw new BadRequestException("Category not found");
        }
        return category;
    }

    private Set<Tag> resolveTags(Product product) {
        Set<Tag> resolvedTags = new HashSet<>();
        if (product.getTags() == null || product.getTags().isEmpty()) {
            return resolvedTags;
        }
        for (Tag tagInput : product.getTags()) {
            if (tagInput.getName() == null || tagInput.getName().isBlank()) {
                continue;
            }
            String tagName = tagInput.getName().trim();
            Tag tag = Tag.find("name = ?1 and tenantId = ?2", tagName, caller.tenantCode()).firstResult();
            if (tag == null) {
                tag = new Tag();
                tag.setId(UUID.randomUUID());
                tag.setName(tagName);
                tag.setTenantId(caller.tenantCode());
                tag.setCreatedBy(caller.userId());
                tag.persist();
            }
            resolvedTags.add(tag);
        }
        return resolvedTags;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
