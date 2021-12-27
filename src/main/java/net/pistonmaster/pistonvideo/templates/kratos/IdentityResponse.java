package net.pistonmaster.pistonvideo.templates.kratos;

import lombok.Getter;

@Getter
public class IdentityResponse {
    private String id;
    private Traits traits;
    private String created_at;
    private Error error;
}

