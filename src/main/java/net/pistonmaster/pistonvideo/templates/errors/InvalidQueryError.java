package net.pistonmaster.pistonvideo.templates.errors;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InvalidQueryError {
    private final String query;
    private final String message = "A query parameter is missing in the request.";
    private final int code = 400;
}
