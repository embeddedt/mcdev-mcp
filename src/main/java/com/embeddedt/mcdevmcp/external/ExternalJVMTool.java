package com.embeddedt.mcdevmcp.external;

import com.embeddedt.mcdevmcp.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class ExternalJVMTool {
    private static final Path TOOLS_CACHE = Constants.CACHE_PATH.resolve("tools");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public record Artifact(String group, String name, String version, String classifier) {
        public Artifact(String group, String name, String version) {
            this(group, name, version, null);
        }
    }

    public abstract Artifact getArtifact();

    public String getMavenRepository() {
        return "https://repo1.maven.org/maven2/";
    }

    protected List<String> getAdditionalJvmArgs() {
        return List.of();
    }

    private Path resolveTool() {
        Artifact artifact = getArtifact();
        String groupPath = artifact.group().replace('.', '/');
        String jarName = artifact.name() + "-" + artifact.version() + Optional.ofNullable(artifact.classifier()).map(s -> "-" + s).orElse("") + ".jar";
        Path localPath = TOOLS_CACHE
                .resolve(groupPath)
                .resolve(artifact.name())
                .resolve(artifact.version())
                .resolve(jarName);

        if (!Files.exists(localPath)) {
            String url = getMavenRepository() + groupPath + "/" + artifact.name()
                    + "/" + artifact.version() + "/" + jarName;
            try {
                Files.createDirectories(localPath.getParent());
                // Stream into a sibling temp file so a failed download never leaves a
                // partial jar at localPath, and so concurrent callers can race safely:
                // whoever wins the atomic rename wins; the losers just delete their temp.
                Path tmp = Files.createTempFile(localPath.getParent(), jarName + ".", ".tmp");
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
                    try (InputStream in = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(tmp, localPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp, localPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Files.deleteIfExists(tmp);
                    throw e;
                } catch (InterruptedException e) {
                    Files.deleteIfExists(tmp);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while downloading " + url, e);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to download tool artifact from " + url, e);
            }
        }

        return localPath;
    }

    protected Process run(List<String> args) {
        // Download the artifact to cache if not already present.
        Path toolJar = resolveTool();

        // Re-use the current JVM executable so the child inherits the same Java version.
        // In a native image ProcessHandle returns the native binary itself, not the JVM, so
        // only trust it when the filename actually starts with "java".
        String javaExe = ProcessHandle.current().info().command()
                .filter(cmd -> Path.of(cmd).getFileName().toString().startsWith("java"))
                .orElseGet(() -> {
                    String javaHome = System.getenv("JAVA_HOME");
                    if (javaHome == null || javaHome.isEmpty()) {
                        javaHome = System.getProperty("java.home", "");
                    }
                    if (!javaHome.isEmpty()) {
                        return Path.of(javaHome, "bin", "java").toString();
                    }
                    return "java"; // last resort: rely on PATH
                });

        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.addAll(getAdditionalJvmArgs());
        command.add("-jar");
        command.add(toolJar.toString());
        command.addAll(args);

        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to launch tool '" + getArtifact().name() + "'", e);
        }
    }

    public record ToolResult(String stdout, String stderr) {}

    protected CompletableFuture<ToolResult> runAndWaitForSuccess(List<String> args) {
        Process process = run(args);

        // Drain stdout and stderr on background threads so the process never
        // blocks on a full pipe buffer.  We capture both so we can report them
        // if the process exits with a non-zero code.
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        Thread stdoutThread = new Thread(() -> {
            try { process.getInputStream().transferTo(stdoutBytes); } catch (IOException ignored) {}
        });
        Thread stderrThread = new Thread(() -> {
            try { process.getErrorStream().transferTo(stderrBytes); } catch (IOException ignored) {}
        });
        stdoutThread.start();
        stderrThread.start();

        return process.onExit().thenCompose(p -> {
            int exitCode = p.exitValue();
            try {
                stdoutThread.join();
                stderrThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(new RuntimeException("Interrupted while waiting for tool", e));
            }
            if (exitCode != 0) {
                return CompletableFuture.failedFuture(new ExternalToolException("neoform-runtime exited with non-zero code " + exitCode,
                        stdoutBytes.toString(StandardCharsets.UTF_8),
                        stderrBytes.toString(StandardCharsets.UTF_8)));
            }
            return CompletableFuture.completedFuture(new ToolResult(stdoutBytes.toString(StandardCharsets.UTF_8), stderrBytes.toString(StandardCharsets.UTF_8)));
        });
    }
}
