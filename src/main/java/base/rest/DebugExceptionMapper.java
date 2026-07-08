package base.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DebugExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        // Log the full error to the server console
        exception.printStackTrace();
        
        // If it's a known HTTP exception (like BadRequestException)
        if (exception instanceof jakarta.ws.rs.WebApplicationException webEx) {
            return Response.status(webEx.getResponse().getStatus())
                    .entity(java.util.Map.of("error", exception.getMessage() != null ? exception.getMessage() : "Unknown error"))
                    .build();
        }
        
        // For other unexpected errors, return 500
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("error", "Internal Server Error", "details", exception.getMessage() != null ? exception.getMessage() : ""))
                .build();
    }
}
