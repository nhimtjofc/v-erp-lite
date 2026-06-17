package base.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Serves translation JSON files from the classpath.
 * <p>
 * Frontend-driven i18n: the backend only hosts resource files,
 * all locale detection and rendering logic lives in the frontend.
 * <p>
 * Cache headers are set aggressively — translation files change
 * infrequently and are fetched once per language/namespace.
 */
@Path("/i18n")
@Produces(MediaType.APPLICATION_JSON)
public class I18nResource {

    @GET
    @Path("/{locale}/{namespace}")
    public Response getTranslations(
            @PathParam("locale") String locale,
            @PathParam("namespace") String namespace,
            @Context Request request
    ) {
        // Security: prevent path traversal — only allow simple names
        if (!locale.matches("^[a-z]{2}(-[A-Z]{2})?$")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid locale\"}")
                    .build();
        }
        if (!namespace.matches("^[a-zA-Z0-9_-]+$")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid namespace\"}")
                    .build();
        }

        String path = "i18n/" + locale + "/" + namespace + ".json";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            // Fallback: try English
            String fallbackPath = "i18n/en/" + namespace + ".json";
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fallbackPath);
            if (is == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Translation not found\"}")
                        .build();
            }
        }

        try {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Strong ETag from SHA-256 content hash (computed fresh each request
            // so file edits are picked up immediately, not stuck in a cache)
            EntityTag etag;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                etag = new EntityTag(hex.toString());
            } catch (Exception ex) {
                etag = new EntityTag("fallback");
            }

            // If-None-Match → 304 if content hasn't changed
            Response.ResponseBuilder preconditional = request.evaluatePreconditions(etag);
            if (preconditional != null) {
                return preconditional.build();
            }

            CacheControl cc = new CacheControl();
            cc.setMaxAge(0); // always revalidate via ETag
            cc.setPrivate(false);
            cc.setMustRevalidate(true); // revalidate with ETag after max-age

            return Response.ok(json)
                    .cacheControl(cc)
                    .tag(etag)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to read translation\"}")
                    .build();
        }
    }
}
