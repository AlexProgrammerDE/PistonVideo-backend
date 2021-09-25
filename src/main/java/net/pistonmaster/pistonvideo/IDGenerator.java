package net.pistonmaster.pistonvideo;

import java.util.UUID;

public class IDGenerator {
    public static String generateSixCharLong() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
