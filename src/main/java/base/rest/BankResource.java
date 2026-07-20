package base.rest;

import base.entity.Bank;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/banks")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner"})
public class BankResource {

    @GET
    public List<Bank> listBanks() {
        return Bank.list("active = true order by name");
    }

    @GET
    @Path("/{id}")
    public Bank getBank(@PathParam("id") UUID id) {
        Bank bank = Bank.find("id = ?1 and active = true", id).firstResult();
        if (bank == null) {
            throw new NotFoundException("Bank not found");
        }
        return bank;
    }
}
