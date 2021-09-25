package net.pistonmaster.pistonvideo.templates;

public record VideoResponse(String id, String title, String description, String videoUrl, String thumbnailUrl, String[] tags, PublicUserResponse uploader) {
}
