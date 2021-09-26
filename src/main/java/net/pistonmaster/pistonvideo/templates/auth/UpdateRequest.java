package net.pistonmaster.pistonvideo.templates.auth;

import lombok.Getter;

@Getter
public class UpdateRequest {
    private String username;
    private String email;
    private String oldPassword;
    private String newPassword;
    private String bioSmall;
    private String bioBig;
}
