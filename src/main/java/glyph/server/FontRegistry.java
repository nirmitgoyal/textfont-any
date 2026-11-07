package glyph.server;

import glyph.FontFace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fonts are loaded once at startup and addressed by name. Callers never supply a path, so there is
 * no path traversal surface, and no request pays the font parse cost.
 */
final class FontRegistry {
    record Entry(String name, FontFace face, String digest) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final Map<String, Entry> fonts;

    private FontRegistry(Map<String, Entry> fonts) {
        this.fonts = fonts;
    }

    /** Fail fast: a font that cannot be parsed, or an empty directory, stops startup. */
    static FontRegistry load(Path dir) throws IOException {
        Map<String, Entry> out = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".ttf")).sorted().toList();
        }
        for (Path p : files) {
            String file = p.getFileName().toString();
            String name = file.substring(0, file.length() - 4);
            if (!NAME.matcher(name).matches()) {
                System.err.println("skipping font with unsupported name: " + file);
                continue;
            }
            out.put(name, new Entry(name, FontFace.load(p), sha256(Files.readAllBytes(p))));
        }
        if (out.isEmpty()) throw new IOException("no usable .ttf fonts in " + dir.toAbsolutePath());
        return new FontRegistry(Map.copyOf(out));
    }

    Entry get(String name) {
        return fonts.get(name);
    }

    List<String> names() {
        return fonts.keySet().stream().sorted().toList();
    }

    int size() {
        return fonts.size();
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
