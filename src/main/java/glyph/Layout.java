package glyph;

import java.util.ArrayList;
import java.util.List;

public record Layout(List<PlacedGlyph> glyphs, double scale, int width, int height, double baselineY) {
    private static final long MAX_DIM = 16384;
    private static final long MAX_AREA = 64_000_000L;

    public record PlacedGlyph(int gid, double penX) {}

    public static Layout of(FontFace face, String text, RenderOptions opts) {
        return of(face, text, opts, MAX_AREA);
    }

    public static Layout of(FontFace face, String text, RenderOptions opts, long maxArea) {
        double scale = opts.sizePx() / (double) face.unitsPerEm();
        double penX = opts.paddingPx();
        List<PlacedGlyph> placed = new ArrayList<>();
        for (int cp : text.codePoints().toArray()) {
            int gid = face.glyphId(cp);
            placed.add(new PlacedGlyph(gid, penX));
            penX += face.advanceWidth(gid) * scale;
        }
        double w = Math.max(1, Math.ceil(penX) + opts.paddingPx());
        double h = Math.ceil((face.ascender() - face.descender()) * scale) + 2.0 * opts.paddingPx();
        if (w > MAX_DIM || h > MAX_DIM || w * h > Math.min(maxArea, MAX_AREA)) {
            throw new CanvasTooLargeException("canvas exceeds limits: " + w + "x" + h);
        }
        double baselineY = opts.paddingPx() + face.ascender() * scale;
        return new Layout(List.copyOf(placed), scale, (int) w, (int) h, baselineY);
    }
}
