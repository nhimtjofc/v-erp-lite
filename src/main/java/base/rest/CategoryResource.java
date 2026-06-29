package base.rest;

import base.entity.Category;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CategoryResource {

    @Inject
    CallerContext caller;

    @GET
    public List<Category> list(@QueryParam("search") String search) {
        String tenantCode = caller.tenantCode();
        if (search != null && !search.isBlank()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            return Category.find(
                    "tenantId = ?1 AND LOWER(name) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\'",
                    tenantCode, escaped
            ).list();
        }
        return Category.list("tenantId", tenantCode);
    }

    @GET
    @Path("/{id}")
    public Category get(@PathParam("id") UUID id) {
        Category category = Category.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (category == null) {
            throw new NotFoundException("Category not found");
        }
        return category;
    }

    @POST
    @RolesAllowed("admin")
    @Transactional
    public Response create(Category category) {
        validate(category);
        category.setId(UUID.randomUUID());
        category.setTenantId(caller.tenantCode());
        category.setCreatedBy(caller.userId());
        category.setParent(resolveParent(category));
        category.persist();
        return Response.created(URI.create("/api/categories/" + category.getId())).entity(category).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Category update(@PathParam("id") UUID id, Category updated) {
        validate(updated);
        Category category = Category.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (category == null) {
            throw new NotFoundException("Category not found");
        }
        Category parent = resolveParent(updated);
        if (parent != null && parent.getId().equals(id)) {
            throw new BadRequestException("Category cannot be its own parent");
        }
        category.setName(updated.getName());
        category.setDescription(updated.getDescription());
        category.setParent(parent);
        category.setActive(updated.isActive());
        category.setUpdatedBy(caller.userId());
        return category;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        long deleted = Category.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Category not found");
        }
        return Response.noContent().build();
    }

    private void validate(Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new BadRequestException("Category name is required");
        }
    }

    private Category resolveParent(Category category) {
        if (category.getParent() == null || category.getParent().getId() == null) {
            return null;
        }
        Category parent = Category.find(
                "id = ?1 and tenantId = ?2",
                category.getParent().getId(), caller.tenantCode()
        ).firstResult();
        if (parent == null) {
            throw new BadRequestException("Parent category not found");
        }
        return parent;
    }
}
