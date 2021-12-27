package net.pistonmaster.pistonvideo;

import java.io.File;

public class Helpers {
    public static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        throw new AssertionError("should have found the executable");
    }
}
