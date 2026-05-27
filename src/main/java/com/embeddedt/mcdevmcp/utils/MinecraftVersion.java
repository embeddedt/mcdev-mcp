package com.embeddedt.mcdevmcp.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed, normalized Minecraft version.
 *
 * <p>Normalization rules (applied at parse time):
 * <ul>
 *   <li>Two-component inputs whose major component is in [2, 25] are prefixed with {@code 1.}
 *       (e.g. {@code 20.1} → {@code 1.20.1}).</li>
 *   <li>Two-component inputs whose major component is ≥ 26 are kept as-is
 *       (e.g. {@code 26.1} stays {@code 26.1}), serialized without a patch segment.</li>
 *   <li>Three-component inputs are kept as-is.</li>
 * </ul>
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
    private static final Pattern PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public static MinecraftVersion parse(String s) {
        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "minecraftVersion must consist of two or three numeric components, got: " + s);
        }
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        boolean hasThird = m.group(3) != null;
        int c = hasThird ? Integer.parseInt(m.group(3)) : 0;

        // Two-component in the modern-MC range [2, 25]: add "1." prefix.
        // (1.x is already a valid two-component form; ≥ 26 is intentionally kept short.)
        if (!hasThird && a >= 2 && a < 26) {
            return new MinecraftVersion(1, a, b);
        }
        return new MinecraftVersion(a, b, c);
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;
        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;
        return Integer.compare(patch, o.patch);
    }

    /**
     * Serializes back to the normalized version string used in artifact notation.
     */
    @Override
    public String toString() {
        // ≥ 26 two-component inputs arrive with patch == 0; keep them short.
        if (major >= 26 && patch == 0) {
            return major + "." + minor;
        }
        return major + "." + minor + "." + patch;
    }
}
