package base.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import base.security.CallerContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Path("/uploads")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private static final java.nio.file.Path UPLOAD_DIR = Paths.get(System.getProperty("user.dir"), "uploads");

    @Inject
    CallerContext caller;

    @POST
    @Path("/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({ "admin", "operator", "businessOwner" })
    public Response uploadImage(@RestForm("file") FileUpload file) throws IOException {
        if (file == null || file.fileName() == null || file.fileName().isBlank()) {
            throw new BadRequestException("No file provided");
        }

        String originalName = file.fileName();
        String extension = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalName.substring(dotIdx).toLowerCase();
        }

        if (!extension.equals(".jpg") && !extension.equals(".jpeg")
                && !extension.equals(".png") && !extension.equals(".gif")
                && !extension.equals(".webp") && !extension.equals(".svg")) {
            throw new BadRequestException("Unsupported image type: " + extension);
        }

        Files.createDirectories(UPLOAD_DIR);

        String filename = UUID.randomUUID().toString() + extension;
        java.nio.file.Path dest = UPLOAD_DIR.resolve(filename);

        try (InputStream in = Files.newInputStream(file.filePath())) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = "/api/uploads/image/" + filename;
        return Response.ok(Map.of("url", url)).build();
    }

    @GET
    @Path("/image/{filename}")
    @Produces("*/*")
    public Response serveImage(@PathParam("filename") String filename) throws IOException {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Invalid filename");
        }

        java.nio.file.Path file = UPLOAD_DIR.resolve(filename);
        if (!Files.exists(file)) {
            throw new NotFoundException("Image not found");
        }

        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        byte[] data = Files.readAllBytes(file);
        return Response.ok(data, contentType)
                .header("Cache-Control", "public, max-age=31536000")
                .build();
    }
}
