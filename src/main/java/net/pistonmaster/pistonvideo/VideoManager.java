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
    private final File uploadDir = new File("upload");

    public VideoManager() {
        uploadDir.mkdir();
    }

    public String upload(Request request, Response response) throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        Part video = request.raw().getPart("video");
        if (video.getSubmittedFileName().endsWith(".mp4")) {
            try (InputStream input = request.raw().getPart("video").getInputStream()) { // getPart needs to use same "name" as input field in form
                Files.copy(input, new File(uploadDir, id + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                return "{\"success\": true}";
            }
        } else {
            return "{\"success\": false}";
        }
    }
}
