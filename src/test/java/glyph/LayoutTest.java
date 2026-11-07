package glyph;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LayoutTest {
    static final String TEXT = "Hello, glyph!";

    @ParameterizedTest
    @ValueSource(strings = {"DejaVuSans.ttf", "DejaVuSansMono.ttf", "DejaVuSerif.ttf"})
    void penAdvancesByAdvanceWidth(String font) throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", font));
        Layout l = Layout.of(f, TEXT, RenderOptions.defaults());
        assertEquals(8.0, l.glyphs().get(0).penX(), 1e-9);
        for (int i = 0; i + 1 < l.glyphs().size(); i++) {
            var g = l.glyphs().get(i);
            assertEquals(g.penX() + f.advanceWidth(g.gid()) * l.scale(), l.glyphs().get(i + 1).penX(), 1e-9);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DejaVuSans.ttf", "DejaVuSansMono.ttf", "DejaVuSerif.ttf"})
    void advanceCellsIncreaseAndDoNotOverlap(String font) throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", font));
        Layout l = Layout.of(f, TEXT, RenderOptions.defaults());
        for (int i = 0; i + 1 < l.glyphs().size(); i++) {
            var g = l.glyphs().get(i);
            double end = g.penX() + f.advanceWidth(g.gid()) * l.scale();
            assertTrue(end > g.penX());
            assertTrue(end <= l.glyphs().get(i + 1).penX() + 1e-9);
        }
    }

    @Test
    void monoAdvancesAllEqual() throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", "DejaVuSansMono.ttf"));
        Layout l = Layout.of(f, TEXT, RenderOptions.defaults());
        int first = f.advanceWidth(l.glyphs().get(0).gid());
        for (var g : l.glyphs()) {
            assertEquals(first, f.advanceWidth(g.gid()));
        }
    }

    @Test
    void canvasSizeAndBaseline() throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", "DejaVuSans.ttf"));
        Layout l = Layout.of(f, TEXT, RenderOptions.defaults());
        double scale = 64.0 / f.unitsPerEm();
        assertEquals(scale, l.scale(), 1e-12);
        var last = l.glyphs().get(l.glyphs().size() - 1);
        assertEquals((int) Math.ceil(last.penX() + f.advanceWidth(last.gid()) * scale) + 8, l.width());
        assertEquals((int) Math.ceil((f.ascender() - f.descender()) * scale) + 16, l.height());
        assertEquals(8 + f.ascender() * scale, l.baselineY(), 1e-9);
    }

    @Test
    void missingCodePointUsesNotdef() throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", "DejaVuSans.ttf"));
        assertEquals(0, f.glyphId(0xE000));
        Layout l = Layout.of(f, "AB", RenderOptions.defaults());
        assertEquals(0, l.glyphs().get(1).gid());
        assertEquals(l.glyphs().get(1).penX() + f.advanceWidth(0) * l.scale(), l.glyphs().get(2).penX(), 1e-9);
    }

    @Test
    void surrogatePairIsOneGlyph() throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", "DejaVuSans.ttf"));
        // U+1F600 is mapped in DejaVuSans; U+20000 is an unmapped astral code point.
        int astral = 0x20000;
        assertEquals(0, f.glyphId(astral));
        String text = "A" + new String(Character.toChars(astral)) + "B";
        assertEquals(4, text.length());
        Layout l = Layout.of(f, text, RenderOptions.defaults());
        assertEquals(3, l.glyphs().size());
        assertEquals(0, l.glyphs().get(1).gid());
    }

    @Test
    void oversizedCanvasRejected() throws IOException {
        FontFace f = FontFace.load(Path.of("fonts", "DejaVuSans.ttf"));
        RenderOptions o = new RenderOptions(512, 8, 0xFF000000, 0xFFFFFFFF);
        assertThrows(IllegalArgumentException.class, () -> Layout.of(f, "W".repeat(1000), o));
    }

    @Test
    void optionsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new RenderOptions(3, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RenderOptions(513, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RenderOptions(64, -1, 0, 0));
        assertDoesNotThrow(() -> new RenderOptions(4, 0, 0, 0));
        assertDoesNotThrow(() -> new RenderOptions(512, 0, 0, 0));
    }
}
