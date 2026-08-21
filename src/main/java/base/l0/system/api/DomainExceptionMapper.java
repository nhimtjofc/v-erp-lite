package base.l0.system.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Turns domain exceptions into the {@link ApiResp} envelope, using the status and code declared by
 * {@link HttpCodeException} on the exception type.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger log = Logger.getLogger(DomainExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException exception) {
        HttpCodeException mapping = exception.getClass().getAnnotation(HttpCodeException.class);
        if (mapping == null) {
            log.error("Unhandled exception", exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResp.fromError(ErrorDetail.of("INTERNAL", "Unexpected error")))
                    .build();
        }
        return Response.status(mapping.status())
                .entity(ApiResp.fromError(ErrorDetail.of(mapping.code(), exception.getMessage())))
                .build();
    }
}

