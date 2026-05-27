package com.embeddedt.mcdevmcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Constants {
    public static final String VERSION = Optional.ofNullable(Constants.class.getPackage())
            .map(Package::getImplementationVersion)
            .orElse("unknown");

    public static final Path CACHE_PATH = Paths.get(System.getProperty("user.home")).resolve(".cache").resolve("mcdev-mcp");

    public static final Path SESSION_PATH = getSessionPath();

    private static Path getSessionPath() {
        try {
            var dir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("mcdev-mcp");
            Files.createDirectories(dir);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                          .forEach(p -> {
                              try {
                                  Files.delete(p);
                              } catch (IOException ignored) {}
                          });
                } catch (IOException ignored) {}
            }));
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
