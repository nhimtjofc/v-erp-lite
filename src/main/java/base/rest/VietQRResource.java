package base.rest;

import base.service.VietQRService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/vietqr")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "VietQR", description = "Offline VietQR generation")
public class VietQRResource {

    @Inject
    VietQRService vietQRService;

    @POST
    @Path("/generate")
    @Operation(summary = "Generate a VietQR code as Base64 image")
    public Response generate(VietQRRequest request) {
        String base64 = vietQRService.generateQrBase64(
            request.getBankCode(),
            request.getAccountNumber(),
            request.getAmount(),
            request.getMessage()
        );
        return Response.ok(new VietQRResponse(base64)).build();
    }

    @Data
    public static class VietQRRequest {
        private String bankCode;
        private String accountNumber;
        private long amount;
        private String message;
    }

    @Data
    public static class VietQRResponse {
        private String qrBase64;

        public VietQRResponse(String qrBase64) {
            this.qrBase64 = qrBase64;
        }
    }
}
