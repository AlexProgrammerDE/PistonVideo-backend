package net.pistonmaster.pistonvideo.templates;

public record UserDataLoginResponse(UserData user) {
    public record UserData(String id) {

    }
}
