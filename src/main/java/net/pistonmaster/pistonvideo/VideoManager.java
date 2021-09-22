package net.pistonmaster.pistonvideo;

import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class VideoManager {
    public final File uploadDir = new File("upload");
    public final File staticDir = new File(uploadDir, "static");
    private final File videoDir = new File(staticDir, "videos");
    private final File thumbnailDir = new File(staticDir, "thumbnails");

    public VideoManager() {
        uploadDir.mkdirs();
        staticDir.mkdirs();
        videoDir.mkdirs();
        thumbnailDir.mkdirs();
    }

    public String upload(Request request, Response response) throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        Part video = request.raw().getPart("video");
        if (video == null || !video.getSubmittedFileName().endsWith(".mp4")) {
            return "{\"success\": false}";
        }

        Part thumbnail = request.raw().getPart("thumbnail");
        if (thumbnail == null || !thumbnail.getSubmittedFileName().endsWith(".png")) {
            return "{\"success\": false}";
        }

        try (InputStream input = video.getInputStream()) {
            Files.copy(input, new File(videoDir, id + ".mp4").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try (InputStream input = thumbnail.getInputStream()) {
            Files.copy(input, new File(thumbnailDir, id + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return "{\"success\": true}";
    }
}
