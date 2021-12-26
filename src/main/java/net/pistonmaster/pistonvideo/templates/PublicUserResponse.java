package net.pistonmaster.pistonvideo.templates;

import java.util.List;

public record PublicUserResponse(String username, String id, String avatarUrl, String bioSmall, String bioBig, List<String> badges) {
}
