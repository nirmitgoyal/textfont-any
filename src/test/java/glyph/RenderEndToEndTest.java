package glyph;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RenderEndToEndTest {
    static int countPixels(BufferedImage img, int argb) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (img.getRGB(x, y) == argb) n++;
        return n;
    }

    @ParameterizedTest @ValueSource(strings = {"DejaVuSans.ttf", "DejaVuSansMono.ttf", "DejaVuSerif.ttf"})
    void rendersHelloGlyph(String font) throws IOException {
        Path out = Path.of("target", "out", font.replace(".ttf", ".png"));
        Files.createDirectories(out.getParent());
        assertEquals(0, Main.run(new String[]{"fonts/" + font, "Hello, glyph!", out.toString()}, System.err));
        BufferedImage img = ImageIO.read(out.toFile());
        assertNotNull(img);
        assertTrue(countPixels(img, 0xFF000000) > 0);
    }

    @Test void badArgsReturnUsageCode() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(2, Main.run(new String[]{"x"}, new PrintStream(buf)));
        assertTrue(buf.toString().contains("usage"));
    }

    @Test void badSizeReturnsErrorAndNoFile() throws IOException {
        Path out = Path.of("target", "out", "bad.png");
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        assertEquals(1, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", out.toString(), "3"}, System.err));
        assertFalse(Files.exists(out));
    }

    @Test void nonIntegerSizeReturnsError() {
        assertEquals(1, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", "target/out/x.png", "abc"}, System.err));
    }

    @Test void sizeArgumentApplied() throws IOException {
        Path big = Path.of("target", "out", "size-default.png");
        Path small = Path.of("target", "out", "size-32.png");
        Files.createDirectories(big.getParent());
        assertEquals(0, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", big.toString()}, System.err));
        assertEquals(0, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", small.toString(), "32"}, System.err));
        assertTrue(ImageIO.read(small.toFile()).getHeight() < ImageIO.read(big.toFile()).getHeight());
    }

    @Test void missingFontReturnsClearError() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(1, Main.run(new String[]{"fonts/nope.ttf", "hi", "target/out/nope.png"}, new PrintStream(buf)));
        assertTrue(buf.toString().contains("no such file"), buf.toString());
    }

    @Test void createsMissingOutputDirectory() throws IOException {
        Path out = Path.of("target", "out", "newdir-" + System.nanoTime(), "x.png");
        assertFalse(Files.exists(out.getParent()));
        assertEquals(0, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", out.toString()}, System.err));
        assertNotNull(ImageIO.read(out.toFile()));
    }

    @Test void failedRenderCreatesNoOutputDirectory() {
        Path out = Path.of("target", "out", "faildir-" + System.nanoTime(), "x.png");
        assertEquals(1, Main.run(new String[]{"fonts/DejaVuSans.ttf", "hi", out.toString(), "3"}, System.err));
        assertFalse(Files.exists(out.getParent()));
    }
}
