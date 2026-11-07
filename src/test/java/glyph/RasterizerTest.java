package glyph;

import static glyph.Outline.Contour;
import static glyph.Outline.Line;
import static glyph.Outline.Point;
import static glyph.Outline.Quad;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RasterizerTest {
    static Contour rect(double x0, double y0, double x1, double y1, boolean reversed) {
        Point a = new Point(x0, y0), b = new Point(x1, y0), c = new Point(x1, y1), d = new Point(x0, y1);
        return reversed
                ? new Contour(a, List.of(new Line(d), new Line(c), new Line(b)))
                : new Contour(a, List.of(new Line(b), new Line(c), new Line(d)));
    }

    @Test void squareFillsSixteenPixels() {
        Bitmap b = new Bitmap(10, 10);
        Rasterizer.fillOutline(b, new Outline(List.of(rect(2, 2, 6, 6, false))));
        assertEquals(16, b.inkCount());
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++)
            assertEquals(x >= 2 && x < 6 && y >= 2 && y < 6, b.get(x, y));
    }

    @Test void reversedInnerSquareLeavesHole() {
        Bitmap b = new Bitmap(10, 10);
        Rasterizer.fillOutline(b, new Outline(List.of(rect(0, 0, 10, 10, false), rect(3, 3, 7, 7, true))));
        assertEquals(84, b.inkCount());
        assertFalse(b.get(5, 5));
        assertTrue(b.get(1, 1));
    }

    @Test void overlappingSameDirectionSquaresFillUnion() {
        Bitmap b = new Bitmap(10, 10);
        Rasterizer.fillOutline(b, new Outline(List.of(rect(0, 0, 6, 6, false), rect(3, 3, 9, 9, false))));
        assertEquals(63, b.inkCount());
        assertTrue(b.get(4, 4));
    }

    @Test void triangleUsesCentreSampleRule() {
        Bitmap b = new Bitmap(10, 10);
        Contour tri = new Contour(new Point(0, 0), List.of(new Line(new Point(8, 0)), new Line(new Point(0, 8))));
        Rasterizer.fillOutline(b, new Outline(List.of(tri)));
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++)
            assertEquals(x + y < 7, b.get(x, y));
        assertEquals(28, b.inkCount());
    }

    @Test void quadCurveIsFlattened() {
        Bitmap b = new Bitmap(24, 24);
        Contour c = new Contour(new Point(0, 0), List.of(
                new Quad(new Point(10, 20), new Point(20, 0)), new Line(new Point(0, 0))));
        Rasterizer.fillOutline(b, new Outline(List.of(c)));
        assertTrue(b.get(10, 5));
        assertFalse(b.get(10, 15));
        assertFalse(b.get(1, 20));
    }

    @Test void clipsShapePartlyOffCanvas() {
        Bitmap b = new Bitmap(10, 10);
        assertDoesNotThrow(() -> Rasterizer.fillOutline(b, new Outline(List.of(rect(-5, -5, 3, 3, false)))));
        assertEquals(9, b.inkCount());
    }

    @Test void emptyOutlineDrawsNothing() {
        Bitmap b = new Bitmap(10, 10);
        Rasterizer.fillOutline(b, Outline.EMPTY);
        assertEquals(0, b.inkCount());
    }
}
