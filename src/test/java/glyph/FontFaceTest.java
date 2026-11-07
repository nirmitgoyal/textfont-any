package glyph;

import static glyph.Outline.Contour;
import static glyph.Outline.Line;
import static glyph.Outline.Point;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FontFaceTest {
    static final Path SANS = Path.of("fonts", "DejaVuSans.ttf");
    static final Path MONO = Path.of("fonts", "DejaVuSansMono.ttf");

    @Test void loadsMetrics() throws IOException {
        FontFace f = FontFace.load(SANS);
        assertTrue(f.unitsPerEm() > 0);
        assertTrue(f.ascender() > 0);
        assertTrue(f.descender() < 0);
    }
    @Test void mapsCodePoints() throws IOException {
        FontFace f = FontFace.load(SANS);
        assertNotEquals(0, f.glyphId('H'));
        assertEquals(0, f.glyphId(0xE000));
    }
    @Test void outlineOfHHasContoursAndSpaceHasNone() throws IOException {
        FontFace f = FontFace.load(SANS);
        assertFalse(f.outline(f.glyphId('H')).contours().isEmpty());
        assertTrue(f.outline(f.glyphId(' ')).contours().isEmpty());
        assertTrue(f.advanceWidth(f.glyphId(' ')) > 0);
    }
    @Test void notdefHasOutline() throws IOException {
        assertFalse(FontFace.load(SANS).outline(0).contours().isEmpty());
    }
    @Test void monoAdvancesEqual() throws IOException {
        FontFace f = FontFace.load(MONO);
        assertEquals(f.advanceWidth(f.glyphId('i')), f.advanceWidth(f.glyphId('W')));
    }
    @Test void nonFontFileThrowsInvalidFont() {
        assertThrows(InvalidFontException.class, () -> FontFace.load(Path.of("fonts", "LICENSE.txt")));
    }
    @Test void missingFileThrowsNoSuchFile() {
        assertThrows(NoSuchFileException.class, () -> FontFace.load(Path.of("fonts", "nope.ttf")));
    }
    @Test void toPixelsFlipsY() {
        var o = new Outline(List.of(new Contour(new Point(0, 100), List.of(new Line(new Point(50, 0))))));
        var p = o.toPixels(0.5, 10, 40).contours().get(0);
        assertEquals(new Point(10, -10), p.start());
        assertEquals(new Point(35, 40), ((Line) p.segments().get(0)).to());
    }
}
