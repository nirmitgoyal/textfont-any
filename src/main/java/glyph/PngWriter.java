package glyph;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;

final class PngWriter {
    private PngWriter() {}

    static BufferedImage toImage(Bitmap b, int inkArgb, int backgroundArgb) {
        BufferedImage img = new BufferedImage(b.width(), b.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < b.height(); y++) {
            for (int x = 0; x < b.width(); x++) {
                img.setRGB(x, y, b.get(x, y) ? inkArgb : backgroundArgb);
            }
        }
        return img;
    }

    static void write(RenderedImage img, OutputStream out) throws IOException {
        if (!ImageIO.write(img, "png", out)) {
            throw new IOException("no PNG writer available");
        }
    }
}
