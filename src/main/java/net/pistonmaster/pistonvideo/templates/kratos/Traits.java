package net.pistonmaster.pistonvideo.templates.kratos;

import lombok.Getter;

@Getter
public class Traits {
    private String username;
    private String email;
    private NameTrait name;

    private static class NameTrait {
        private String first;
        private String last;
    }
}
