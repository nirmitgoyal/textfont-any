package glyph;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;

public final class GlyphRenderer {
    private static final int MAX_CODE_POINTS = 1000;

    private GlyphRenderer() {}

    public static BufferedImage render(Path fontFile, String text, RenderOptions opts) throws IOException {
        Objects.requireNonNull(fontFile, "fontFile");
        return render(FontFace.load(fontFile), text, opts, Long.MAX_VALUE);
    }

    /** Render with an already-loaded font (immutable, safe to share across threads) and a pixel budget. */
    public static BufferedImage render(FontFace face, String text, RenderOptions opts, long maxPixels) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(opts, "opts");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        if (text.codePointCount(0, text.length()) > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("text exceeds " + MAX_CODE_POINTS + " code points");
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("single line only");
        }
        Layout layout = Layout.of(face, text, opts, maxPixels);
        Bitmap bitmap = new Bitmap(layout.width(), layout.height());
        for (Layout.PlacedGlyph g : layout.glyphs()) {
            Rasterizer.fillOutline(bitmap, face.outline(g.gid()).toPixels(layout.scale(), g.penX(), layout.baselineY()));
        }
        return PngWriter.toImage(bitmap, opts.inkArgb(), opts.backgroundArgb());
    }

    public static void renderToPng(Path fontFile, String text, RenderOptions opts, OutputStream out) throws IOException {
        BufferedImage img = render(fontFile, text, opts);
        PngWriter.write(img, out);
    }

    public static void renderToPng(FontFace face, String text, RenderOptions opts, long maxPixels,
                                   OutputStream out) throws IOException {
        PngWriter.write(render(face, text, opts, maxPixels), out);
    }
}
