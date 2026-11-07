package glyph;

import java.util.ArrayList;
import java.util.List;

public record Outline(List<Contour> contours) {
    public static final Outline EMPTY = new Outline(List.of());

    public Outline {
        contours = List.copyOf(contours);
    }

    public record Point(double x, double y) {}

    public sealed interface Segment permits Line, Quad, Cubic {}

    public record Line(Point to) implements Segment {}

    public record Quad(Point ctrl, Point to) implements Segment {}

    public record Cubic(Point c1, Point c2, Point to) implements Segment {}

    /** Implicitly closed back to {@code start}. */
    public record Contour(Point start, List<Segment> segments) {
        public Contour {
            segments = List.copyOf(segments);
        }
    }

    /** Maps (x, y) to (penX + x*scale, baselineY - y*scale). */
    public Outline toPixels(double scale, double penX, double baselineY) {
        List<Contour> out = new ArrayList<>(contours.size());
        for (Contour c : contours) {
            List<Segment> segs = new ArrayList<>(c.segments().size());
            for (Segment s : c.segments()) {
                segs.add(switch (s) {
                    case Line l -> new Line(map(l.to(), scale, penX, baselineY));
                    case Quad q -> new Quad(map(q.ctrl(), scale, penX, baselineY),
                            map(q.to(), scale, penX, baselineY));
                    case Cubic k -> new Cubic(map(k.c1(), scale, penX, baselineY),
                            map(k.c2(), scale, penX, baselineY),
                            map(k.to(), scale, penX, baselineY));
                });
            }
            out.add(new Contour(map(c.start(), scale, penX, baselineY), segs));
        }
        return new Outline(out);
    }

    private static Point map(Point p, double scale, double penX, double baselineY) {
        return new Point(penX + p.x() * scale, baselineY - p.y() * scale);
    }
}
