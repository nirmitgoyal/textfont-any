package glyph;

import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.GlyphTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

public final class FontFace {
    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int[] advances;
    private final Outline[] outlines;
    private final Map<Integer, Integer> cmap;

    private FontFace(int unitsPerEm, int ascender, int descender, int[] advances,
                     Outline[] outlines, Map<Integer, Integer> cmap) {
        this.unitsPerEm = unitsPerEm;
        this.ascender = ascender;
        this.descender = descender;
        this.advances = advances;
        this.outlines = outlines;
        this.cmap = cmap;
    }

    public static FontFace load(Path ttf) throws IOException {
        if (!Files.exists(ttf) || !Files.isReadable(ttf)) {
            throw new NoSuchFileException(ttf.toString());
        }
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBufferedFile(ttf))) {
            GlyphTable glyf = font.getGlyph();
            if (glyf == null) {
                throw new InvalidFontException("unsupported outline format");
            }
            CmapLookup lookup = font.getUnicodeCmapLookup();
            if (lookup == null) {
                throw new InvalidFontException("no Unicode cmap");
            }
            int n = font.getNumberOfGlyphs();
            int[] adv = new int[n];
            Outline[] outs = new Outline[n];
            for (int gid = 0; gid < n; gid++) {
                adv[gid] = font.getAdvanceWidth(gid);
                outs[gid] = toOutline(glyf.getGlyph(gid));
            }
            Map<Integer, Integer> map = new HashMap<>();
            for (int cp = 0; cp <= 0x10FFFF; cp++) {
                int gid = lookup.getGlyphId(cp);
                if (gid > 0) {
                    map.put(cp, gid);
                }
            }
            return new FontFace(font.getUnitsPerEm(), font.getHorizontalHeader().getAscender(),
                    font.getHorizontalHeader().getDescender(), adv, outs, map);
        } catch (InvalidFontException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidFontException("not a parseable TrueType font: " + ttf, e);
        }
    }

    private static Outline toOutline(GlyphData g) {
        if (g == null) {
            return Outline.EMPTY;
        }
        List<Outline.Contour> contours = new ArrayList<>();
        Outline.Point start = null;
        List<Outline.Segment> segs = new ArrayList<>();
        double[] c = new double[6];
        for (PathIterator it = g.getPath().getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> {
                    flush(contours, start, segs);
                    start = new Outline.Point(c[0], c[1]);
                    segs = new ArrayList<>();
                }
                case PathIterator.SEG_LINETO -> segs.add(new Outline.Line(new Outline.Point(c[0], c[1])));
                case PathIterator.SEG_QUADTO -> segs.add(new Outline.Quad(
                        new Outline.Point(c[0], c[1]), new Outline.Point(c[2], c[3])));
                case PathIterator.SEG_CUBICTO -> segs.add(new Outline.Cubic(
                        new Outline.Point(c[0], c[1]), new Outline.Point(c[2], c[3]),
                        new Outline.Point(c[4], c[5])));
                case PathIterator.SEG_CLOSE -> {
                    flush(contours, start, segs);
                    start = null;
                    segs = new ArrayList<>();
                }
                default -> { }
            }
        }
        flush(contours, start, segs);
        return contours.isEmpty() ? Outline.EMPTY : new Outline(contours);
    }

    private static void flush(List<Outline.Contour> out, Outline.Point start, List<Outline.Segment> segs) {
        if (start != null && !segs.isEmpty()) {
            out.add(new Outline.Contour(start, segs));
        }
    }

    public int unitsPerEm() { return unitsPerEm; }

    public int ascender() { return ascender; }

    public int descender() { return descender; }

    public int glyphId(int codePoint) {
        return cmap.getOrDefault(codePoint, 0);
    }

    public int advanceWidth(int gid) { return advances[gid]; }

    public Outline outline(int gid) { return outlines[gid]; }
}
