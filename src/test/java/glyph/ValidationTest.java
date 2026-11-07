package glyph;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ValidationTest {
    static final Path SANS = Path.of("fonts", "DejaVuSans.ttf");
    static final RenderOptions D = RenderOptions.defaults();

    @Test void missingFont() {
        var e = assertThrows(NoSuchFileException.class, () -> GlyphRenderer.render(Path.of("fonts/none.ttf"), "a", D));
        assertNotNull(e.getMessage());
    }

    @Test void notAFont() {
        var e = assertThrows(InvalidFontException.class, () -> GlyphRenderer.render(Path.of("fonts/LICENSE.txt"), "a", D));
        assertNotNull(e.getMessage());
    }

    @Test void nullText() {
        var e = assertThrows(NullPointerException.class, () -> GlyphRenderer.render(SANS, null, D));
        assertNotNull(e.getMessage());
    }

    @Test void nullOptions() {
        var e = assertThrows(NullPointerException.class, () -> GlyphRenderer.render(SANS, "a", null));
        assertNotNull(e.getMessage());
    }

    @Test void emptyText() {
        var e = assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "", D));
        assertNotNull(e.getMessage());
    }

    @Test void tooLongText() {
        var e = assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "a".repeat(1001), D));
        assertNotNull(e.getMessage());
    }

    @Test void maxLengthTextOk() {
        assertDoesNotThrow(() -> GlyphRenderer.render(SANS, "a".repeat(1000), new RenderOptions(8, 0, 0xFF000000, 0xFFFFFFFF)));
    }

    @Test void lengthCountsCodePointsNotChars() {
        // 1000 astral code points = 2000 chars: must be accepted
        assertDoesNotThrow(() -> GlyphRenderer.render(SANS, "😀".repeat(1000), new RenderOptions(8, 0, 0xFF000000, 0xFFFFFFFF)));
        assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "😀".repeat(1001), D));
    }

    @Test void newlineRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "a\nb", D));
        assertEquals("single line only", e.getMessage());
    }

    @Test void carriageReturnRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "a\rb", D));
        assertEquals("single line only", e.getMessage());
    }

    @Test void sizeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "a", new RenderOptions(600, 8, 0, 0)));
    }

    @Test void negativePaddingRejected() {
        assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.render(SANS, "a", new RenderOptions(64, -1, 0xFF000000, 0xFFFFFFFF)));
    }

    @Test void canvasTooLarge() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> GlyphRenderer.render(SANS, "W".repeat(1000), new RenderOptions(512, 8, 0xFF000000, 0xFFFFFFFF)));
        assertNotNull(e.getMessage());
    }

    @Test void failedRenderToPngWritesNothing() {
        var out = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> GlyphRenderer.renderToPng(SANS, "", D, out));
        assertEquals(0, out.size());
    }
}
