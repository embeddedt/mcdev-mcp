package com.embeddedt.mcdevmcp.external;

import com.embeddedt.mcdevmcp.Constants;
import com.embeddedt.mcdevmcp.enums.ModLoader;
import com.embeddedt.mcdevmcp.jsonrpc.ToolCallResult;
import com.embeddedt.mcdevmcp.jsonrpc.ToolDefinition;
import com.embeddedt.mcdevmcp.mcp.ToolBuilder;
import com.embeddedt.mcdevmcp.utils.MinecraftVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

public class GetMinecraftSourcesTool extends ExternalJVMTool {
    public static final String NEOFORM_RUNTIME_VERSION = "2.0.19";

    public static final GetMinecraftSourcesTool INSTANCE = new GetMinecraftSourcesTool();

    @Override
    public Artifact getArtifact() {
        return new Artifact("net.neoforged", "neoform-runtime", NEOFORM_RUNTIME_VERSION, "all");
    }

    @Override
    public String getMavenRepository() {
        return "https://maven.neoforged.net/releases/";
    }

    private static final MinecraftVersion V1_20_1 = new MinecraftVersion(1, 20, 1);
    private static final MinecraftVersion V1_20_2 = new MinecraftVersion(1, 20, 2);

    public record Params(String minecraftVersion, ModLoader loader, String loaderVersion) {
        @Override
        public String toString() {
            var sb = new StringBuilder(minecraftVersion).append('-').append(loader.name().toLowerCase());
            if (loaderVersion != null && !loaderVersion.isEmpty()) {
                sb.append('-').append(loaderVersion);
            }
            return sb.toString();
        }
    }

    public CompletableFuture<Path> getSources(Params params) {
        // --- 1. Parse & normalize ---
        MinecraftVersion version = MinecraftVersion.parse(params.minecraftVersion());

        var loader = params.loader();

        // --- 2. Validate loader ---
        if (loader != ModLoader.VANILLA && loader != ModLoader.FORGE && loader != ModLoader.NEOFORGE) {
            throw new IllegalArgumentException(
                    "Unsupported loader: " + loader + ". Only VANILLA, FORGE, and NEOFORGE are accepted.");
        }

        // NEOFORGE: only for 1.20.2 and higher
        if (loader == ModLoader.NEOFORGE && version.compareTo(V1_20_2) < 0) {
            throw new IllegalArgumentException(
                    "NEOFORGE is only supported for Minecraft 1.20.2 and higher (got " + version + ")");
        }
        // FORGE: only for 1.20.1 and lower
        if (loader == ModLoader.FORGE && version.compareTo(V1_20_1) > 0) {
            throw new IllegalArgumentException(
                    "FORGE is only supported for Minecraft 1.20.1 and lower (got " + version + ")");
        }

        // --- 3. Prepare output path ---
        Path sourcesJar;
        try {
            sourcesJar = Files.createTempFile("mcdev-sources-", ".jar");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary sources jar", e);
        }

        // --- 4. Build neoform-runtime arguments ---
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--dist");
        args.add("joined");

        String sourcesResultKey;
        switch (loader) {
            case NEOFORGE -> {
                args.add("--neoforge");
                args.add("net.neoforged:neoforge:" + params.loaderVersion() + ":userdev");
                sourcesResultKey = "gameSourcesWithNeoForge";
            }
            case FORGE -> {
                args.add("--add-repository");
                args.add("https://maven.minecraftforge.net");
                args.add("--neoforge");
                args.add("net.minecraftforge:forge:" + version + "-" + params.loaderVersion() + ":userdev");
                sourcesResultKey = "gameSourcesWithNeoForge";
            }
            case VANILLA -> { // VANILLA
                args.add("--neoform");
                args.add("de.oceanlabs.mcp:mcp_config:" + version + "@zip");
                sourcesResultKey = "gameSources";
            }
            default -> throw new IllegalArgumentException("Loader " + loader.name() + " not currently supported");
        }

        args.add("--write-result=" + sourcesResultKey + ":" + sourcesJar);

        Path extractedDir = Constants.SESSION_PATH.resolve(params + "-sources");

        return runAndWaitForSuccess(args).thenApply($ -> {
            try {
                Files.createDirectories(extractedDir);
                try (var zip = new ZipFile(sourcesJar.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        Path dest = extractedDir.resolve(entry.getName()).normalize();
                        if (!dest.startsWith(extractedDir)) {
                            throw new IOException("Zip entry escapes extraction directory: " + entry.getName());
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            try (var in = zip.getInputStream(entry)) {
                                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
                Files.delete(sourcesJar);
                return extractedDir;
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract sources jar", e);
            }
        });
    }

    public static ToolDefinition tool() {
        return ToolBuilder.tool("get_minecraft_source_path",
                "Get folder path of Minecraft & modloader sources",
                Params.class,
                params -> INSTANCE.getSources(params)
                        .thenApply(path -> ToolCallResult.text(path.toAbsolutePath().toString()))
        );
    }
}
