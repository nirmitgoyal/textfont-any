package glyph;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("serve")) {
            glyph.server.Server.main(args);
            return;
        }
        System.exit(run(args, System.err));
    }

    static int run(String[] args, PrintStream err) {
        if (args.length < 3 || args.length > 4) {
            err.println("usage: java -jar glyph.jar serve   (HTTP service; see README)");
            err.println("   or: java -jar glyph.jar <font.ttf> \"<text>\" <out.png> [sizePx]");
            return 2;
        }
        RenderOptions d = RenderOptions.defaults();
        int size = d.sizePx();
        if (args.length == 4) {
            try {
                size = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                err.println("error: sizePx must be an integer: " + args[3]);
                return 1;
            }
        }
        try {
            RenderOptions opts = new RenderOptions(size, d.paddingPx(), d.inkArgb(), d.backgroundArgb());
            BufferedImage img = GlyphRenderer.render(Path.of(args[0]), args[1], opts);
            Path outPath = Path.of(args[2]);
            Path parent = outPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(outPath)) {
                PngWriter.write(img, out);
            }
            return 0;
        } catch (NoSuchFileException e) {
            err.println("error: no such file: " + e.getFile());
            return 1;
        } catch (IOException | IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }
}
