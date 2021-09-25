package net.pistonmaster.pistonvideo.templates.auth;

import lombok.Getter;

@Getter
public class SignupRequest {
    private String username;
    private String email;
    private String password;
}
