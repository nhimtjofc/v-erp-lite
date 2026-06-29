package base.rest;

import base.entity.Tag;
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

@Path("/tags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TagResource {

    @Inject
    CallerContext caller;

    @GET
    public List<Tag> list(@QueryParam("search") String search) {
        String tenantCode = caller.tenantCode();
        if (search != null && !search.isBlank()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            return Tag.find(
                    "tenantId = ?1 AND LOWER(name) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\'",
                    tenantCode, escaped
            ).list();
        }
        return Tag.list("tenantId", tenantCode);
    }

    @GET
    @Path("/{id}")
    public Tag get(@PathParam("id") UUID id) {
        Tag tag = Tag.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (tag == null) {
            throw new NotFoundException("Tag not found");
        }
        return tag;
    }

    @POST
    @RolesAllowed("admin")
    @Transactional
    public Response create(Tag tag) {
        validate(tag);
        Tag existing = Tag.find("name = ?1 and tenantId = ?2", tag.getName().trim(), caller.tenantCode()).firstResult();
        if (existing != null) {
            throw new BadRequestException("Tag already exists");
        }
        tag.setId(UUID.randomUUID());
        tag.setTenantId(caller.tenantCode());
        tag.setCreatedBy(caller.userId());
        tag.persist();
        return Response.created(URI.create("/api/tags/" + tag.getId())).entity(tag).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        long deleted = Tag.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Tag not found");
        }
        return Response.noContent().build();
    }

    private void validate(Tag tag) {
        if (tag.getName() == null || tag.getName().isBlank()) {
            throw new BadRequestException("Tag name is required");
        }
    }
}
