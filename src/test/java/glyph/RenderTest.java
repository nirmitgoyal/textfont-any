package glyph;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RenderTest {
    static final Path SANS = Path.of("fonts", "DejaVuSans.ttf");
    static final Path SERIF = Path.of("fonts", "DejaVuSerif.ttf");
    static final RenderOptions D = RenderOptions.defaults();

    static int inkCount(BufferedImage img, int ink) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (img.getRGB(x, y) == ink) n++;
        return n;
    }

    static boolean rowHasInk(BufferedImage img, int y, int ink) {
        for (int x = 0; x < img.getWidth(); x++) if (img.getRGB(x, y) == ink) return true;
        return false;
    }

    @Test void missingCodePointDrawsNotdef() throws IOException {
        FontFace sans = FontFace.load(SANS);
        assertEquals(0, sans.glyphId(0xE000));
        assertEquals(0, sans.glyphId(0xE001));
        BufferedImage a = GlyphRenderer.render(SANS, "\ue000", D);
        BufferedImage b = GlyphRenderer.render(SANS, "\ue001", D);
        assertTrue(inkCount(a, 0xFF000000) > 0);
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                assertEquals(a.getRGB(x, y), b.getRGB(x, y));
    }

    @Test void hHasInkAboveBaselineOnly() throws IOException {
        FontFace sans = FontFace.load(SANS);
        Layout l = Layout.of(sans, "H", D);
        BufferedImage img = GlyphRenderer.render(SANS, "H", D);
        assertTrue(rowHasInk(img, (int) Math.floor(l.baselineY()) - 1, 0xFF000000));
        for (int y = (int) Math.ceil(l.baselineY()) + 1; y < img.getHeight(); y++)
            assertFalse(rowHasInk(img, y, 0xFF000000), "ink in row " + y);
    }

    @Test void onlyInkAndBackgroundPixels() throws IOException {
        BufferedImage img = GlyphRenderer.render(SERIF, "Hello, glyph!", D);
        int ink = 0, bg = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                assertTrue(p == 0xFF000000 || p == 0xFFFFFFFF);
                if (p == 0xFF000000) ink++; else bg++;
            }
        assertTrue(ink > 0);
        assertTrue(bg > 0);
    }

    @Test void customColoursUsed() throws IOException {
        BufferedImage img = GlyphRenderer.render(SANS, "Hello", new RenderOptions(32, 4, 0xFFFF0000, 0xFF00FF00));
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                assertTrue(p == 0xFFFF0000 || p == 0xFF00FF00);
            }
        assertTrue(inkCount(img, 0xFFFF0000) > 0);
    }

    @Test void compositeGlyphHasInkAboveXHeight() throws IOException {
        BufferedImage e = GlyphRenderer.render(SANS, "e", D);
        BufferedImage ea = GlyphRenderer.render(SANS, "é", D);
        assertEquals(e.getHeight(), ea.getHeight());
        assertTrue(inkCount(ea, 0xFF000000) > inkCount(e, 0xFF000000));
        int topE = 0;
        while (!rowHasInk(e, topE, 0xFF000000)) topE++;
        int topEa = 0;
        while (!rowHasInk(ea, topEa, 0xFF000000)) topEa++;
        assertTrue(topEa < topE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DejaVuSans.ttf", "DejaVuSansMono.ttf", "DejaVuSerif.ttf"})
    void tallAndDescendingGlyphsDoNotThrow(String font) throws IOException {
        BufferedImage img = assertDoesNotThrow(() -> GlyphRenderer.render(Path.of("fonts", font), "ÅÉÇjgpq", D));
        assertTrue(inkCount(img, 0xFF000000) > 0);
    }

    @Test void spacesOnlyRendersBlankImage() throws IOException {
        Layout l = Layout.of(FontFace.load(SANS), "   ", D);
        BufferedImage img = GlyphRenderer.render(SANS, "   ", D);
        assertEquals(l.width(), img.getWidth());
        assertEquals(0, inkCount(img, 0xFF000000));
    }

    @Test void renderToPngWritesDecodablePng() throws IOException {
        var out = new ByteArrayOutputStream();
        GlyphRenderer.renderToPng(SANS, "Hello", D, out);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
        BufferedImage direct = GlyphRenderer.render(SANS, "Hello", D);
        assertNotNull(decoded);
        assertEquals(direct.getWidth(), decoded.getWidth());
        assertEquals(direct.getHeight(), decoded.getHeight());
    }
}
