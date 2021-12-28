package net.pistonmaster.pistonvideo.templates.errors;

public class RateLimitError {
    private final String message = "You have exceeded the ratelimit for this endpoint. Please try again later.";
    private final int code = 429;
}
