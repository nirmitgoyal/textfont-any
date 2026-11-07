package glyph;

public record RenderOptions(int sizePx, int paddingPx, int inkArgb, int backgroundArgb) {
    public RenderOptions {
        if (sizePx < 4 || sizePx > 512) {
            throw new IllegalArgumentException("sizePx must be in [4, 512]: " + sizePx);
        }
        if (paddingPx < 0) {
            throw new IllegalArgumentException("paddingPx must be >= 0: " + paddingPx);
        }
    }

    public static RenderOptions defaults() {
        return new RenderOptions(64, 8, 0xFF000000, 0xFFFFFFFF);
    }
}
