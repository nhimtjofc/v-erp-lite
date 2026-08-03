package base.rest;

import base.entity.Customer;
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

@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class CustomerResource {

    @Inject
    CallerContext caller;

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    @Transactional
    public Customer getMyCustomerProfile() {
        String email = caller.userEmail();
        String tenantCode = caller.tenantCode();
        Customer customer = Customer.find("email = ?1 and tenantId = ?2", email, tenantCode).firstResult();
        if (customer == null) {
            customer = new Customer();
            customer.setId(UUID.randomUUID());
            customer.setTenantId(tenantCode);
            customer.setEmail(email);
            String preferredUsername = caller.preferredUsername();
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                customer.setFirstName(preferredUsername);
                customer.setLastName("");
            } else {
                customer.setFirstName("Customer");
                customer.setLastName("");
            }
            customer.setActive(true);
            customer.setCreatedBy(caller.userId());
            customer.persist();
        }
        return customer;
    }

    @GET
    public List<Customer> listCustomers(@QueryParam("search") String search) {
        String tenantCode = caller.tenantCode();
        if (search != null && !search.isBlank()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            return Customer.find(
                    "tenantId = ?1 AND (LOWER(firstName) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(lastName) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(email) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\')",
                    tenantCode, escaped
            ).list();
        }
        return Customer.list("tenantId", tenantCode);
    }

    @GET
    @Path("/{id}")
    public Customer getCustomer(@PathParam("id") UUID id) {
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        return customer;
    }

    @POST
    @Transactional
    public Response createCustomer(Customer customer) {
        customer.setId(UUID.randomUUID());
        customer.setTenantId(caller.tenantCode());
        customer.setCreatedBy(caller.userId());
        customer.persist();
        return Response.created(URI.create("/api/customers/" + customer.getId())).entity(customer).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Customer updateCustomer(@PathParam("id") UUID id, Customer updated) {
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        customer.setFirstName(updated.getFirstName());
        customer.setLastName(updated.getLastName());
        customer.setEmail(updated.getEmail());
        customer.setPhone(updated.getPhone());
        customer.setAddress(updated.getAddress());
        customer.setProvinceCode(updated.getProvinceCode());
        customer.setDistrictCode(updated.getDistrictCode());
        customer.setWardCode(updated.getWardCode());
        customer.setStreetAddress(updated.getStreetAddress());
        customer.setBirthDate(updated.getBirthDate());
        customer.setNotes(updated.getNotes());
        customer.setActive(updated.isActive());
        customer.setUpdatedBy(caller.userId());
        return customer;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteCustomer(@PathParam("id") UUID id) {
        long deleted = Customer.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Customer not found");
        }
        return Response.noContent().build();
    }
}
