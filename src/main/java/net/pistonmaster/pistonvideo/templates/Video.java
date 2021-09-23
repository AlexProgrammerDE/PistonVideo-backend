package net.pistonmaster.pistonvideo.templates;

public record Video(String id, String title, String description,
                    String videoUrl, String thumbnailUrl, String[] tags) {
}
