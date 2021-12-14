package net.pistonmaster.pistonvideo.templates;

public record UserDataLoginResponse(UserData user) {
    public static record UserData(String id) {

    }
}
