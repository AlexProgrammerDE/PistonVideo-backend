package net.pistonmaster.pistonvideo.templates.auth;

import lombok.Getter;

@Getter
public class InfoUpdateRequest {
    private String username;
    private String email;
    private String oldPassword;
    private String newPassword;
}
