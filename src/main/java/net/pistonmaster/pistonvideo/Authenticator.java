package net.pistonmaster.pistonvideo;

import java.util.Optional;
import java.util.UUID;

public class Authenticator {
    public boolean isValid(String token) {
        return true;
    }

    public Optional<String> generateToken(String email, String password) {
        if (isValid(email, password)) {
            return Optional.of(UUID.randomUUID().toString().replace("-", ""));
        } else {
            return Optional.empty();
        }
    }

    public void invalidate(String token) {

    }

    private boolean isValid(String email, String password) {
        return true;
    }

    public RejectReason createUser(String username, String email, String password) {
        return RejectReason.NONE;
    }

    public enum RejectReason {
        NONE,
        INVALID_EMAIL,
        INVALID_PASSWORD,
        INVALID_USERNAME,
        ALREADY_EXISTS_USERNAME,
        ALREADY_EXISTS_EMAIL
    }
}
