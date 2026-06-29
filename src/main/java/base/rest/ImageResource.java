package base.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Path("/images")
@Produces(MediaType.APPLICATION_JSON)
public class ImageResource {

    private static final java.nio.file.Path UPLOAD_DIR = Paths.get("uploads");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    static {
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @PermitAll
    public Response uploadImage(@RestForm("file") FileUpload file) {
        if (file == null || file.filePath() == null) {
            throw new BadRequestException("No file provided");
        }

        String contentType = file.contentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("Invalid file type. Allowed: JPEG, PNG, GIF, WebP");
        }

        long fileSize = file.size();
        if (fileSize > MAX_FILE_SIZE) {
            throw new BadRequestException("File too large. Maximum size: 5MB");
        }

        String extension = "";
        if (contentType != null) {
            switch (contentType) {
                case "image/jpeg": extension = ".jpg"; break;
                case "image/png": extension = ".png"; break;
                case "image/gif": extension = ".gif"; break;
                case "image/webp": extension = ".webp"; break;
            }
        }

        String filename = UUID.randomUUID().toString() + extension;
        java.nio.file.Path targetPath = UPLOAD_DIR.resolve(filename);

        try (InputStream in = Files.newInputStream(file.filePath())) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to save file");
        }

        String imageUrl = "/api/images/" + filename;
        return Response.ok(java.util.Map.of(
            "url", imageUrl,
            "filename", filename
        )).build();
    }

    @GET
    @Path("/{filename}")
    @PermitAll
    public Response getImage(@PathParam("filename") String filename) {
        java.nio.file.Path filePath = UPLOAD_DIR.resolve(filename).normalize();
        if (!filePath.startsWith(UPLOAD_DIR)) {
            throw new NotFoundException("Invalid path");
        }
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new NotFoundException("Image not found");
        }

        String contentType = guessContentType(filename);
        StreamingOutput stream = output -> {
            try (InputStream in = Files.newInputStream(filePath)) {
                in.transferTo(output);
            }
        };

        return Response.ok(stream, contentType).build();
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
