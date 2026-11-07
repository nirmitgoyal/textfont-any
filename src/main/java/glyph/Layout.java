package glyph;

import java.util.ArrayList;
import java.util.List;

public record Layout(List<PlacedGlyph> glyphs, double scale, int width, int height, double baselineY) {
    private static final long MAX_DIM = 16384;
    private static final long MAX_AREA = 64_000_000L;

    public record PlacedGlyph(int gid, double penX) {}

    public static Layout of(FontFace face, String text, RenderOptions opts) {
        double scale = opts.sizePx() / (double) face.unitsPerEm();
        double penX = opts.paddingPx();
        List<PlacedGlyph> placed = new ArrayList<>();
        for (int cp : text.codePoints().toArray()) {
            int gid = face.glyphId(cp);
            placed.add(new PlacedGlyph(gid, penX));
            penX += face.advanceWidth(gid) * scale;
        }
        double w = Math.ceil(penX) + opts.paddingPx();
        double h = Math.ceil((face.ascender() - face.descender()) * scale) + 2.0 * opts.paddingPx();
        if (w > MAX_DIM || h > MAX_DIM || w * h > MAX_AREA) {
            throw new IllegalArgumentException("canvas exceeds limits: " + w + "x" + h);
        }
        double baselineY = opts.paddingPx() + face.ascender() * scale;
        return new Layout(List.copyOf(placed), scale, (int) w, (int) h, baselineY);
    }
}
