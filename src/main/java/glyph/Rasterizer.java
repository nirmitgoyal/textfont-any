package glyph;

import static glyph.Outline.Contour;
import static glyph.Outline.Cubic;
import static glyph.Outline.Line;
import static glyph.Outline.Point;
import static glyph.Outline.Quad;
import static glyph.Outline.Segment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Rasterizer {
    private static final double TOLERANCE = 0.25;

    private Rasterizer() {}

    /** Non-zero winding fill of an outline already in pixel space (y down), sampled at pixel centres. */
    public static void fillOutline(Bitmap target, Outline pixelOutline) {
        List<double[]> edges = new ArrayList<>();
        for (Contour c : pixelOutline.contours()) flatten(c, edges);

        int w = target.width(), h = target.height();
        @SuppressWarnings("unchecked")
        List<double[]>[] rows = new List[h];
        for (double[] e : edges) {
            double x0 = e[0], y0 = e[1], x1 = e[2], y1 = e[3];
            double ymin = Math.min(y0, y1), ymax = Math.max(y0, y1);
            int dir = y1 > y0 ? 1 : -1;
            int rFirst = Math.max(0, (int) Math.ceil(ymin - 0.5));
            int rLast = Math.min(h - 1, (int) Math.ceil(ymax - 0.5) - 1);
            for (int r = rFirst; r <= rLast; r++) {
                double sy = r + 0.5;
                double x = x0 + (sy - y0) * (x1 - x0) / (y1 - y0);
                if (rows[r] == null) rows[r] = new ArrayList<>();
                rows[r].add(new double[] {x, dir});
            }
        }
        for (int r = 0; r < h; r++) {
            if (rows[r] == null) continue;
            double[][] xs = rows[r].toArray(new double[0][]);
            Arrays.sort(xs, (a, b) -> Double.compare(a[0], b[0]));
            int winding = 0;
            for (int i = 0; i < xs.length - 1; i++) {
                winding += (int) xs[i][1];
                if (winding == 0) continue;
                int cFirst = Math.max(0, (int) Math.ceil(xs[i][0] - 0.5));
                int cLast = Math.min(w - 1, (int) Math.ceil(xs[i + 1][0] - 0.5) - 1);
                for (int c = cFirst; c <= cLast; c++) target.set(c, r);
            }
        }
    }

    private static void flatten(Contour c, List<double[]> edges) {
        Point cur = c.start();
        for (Segment s : c.segments()) {
            switch (s) {
                case Line l -> {
                    addEdge(edges, cur, l.to());
                    cur = l.to();
                }
                case Quad q -> {
                    double dx = cur.x() - 2 * q.ctrl().x() + q.to().x();
                    double dy = cur.y() - 2 * q.ctrl().y() + q.to().y();
                    int n = Math.max(1, (int) Math.ceil(Math.sqrt(Math.hypot(dx, dy) / (4 * TOLERANCE))));
                    Point prev = cur;
                    for (int i = 1; i <= n; i++) {
                        double t = (double) i / n, u = 1 - t;
                        Point p = i == n ? q.to() : new Point(
                                u * u * cur.x() + 2 * u * t * q.ctrl().x() + t * t * q.to().x(),
                                u * u * cur.y() + 2 * u * t * q.ctrl().y() + t * t * q.to().y());
                        addEdge(edges, prev, p);
                        prev = p;
                    }
                    cur = q.to();
                }
                case Cubic k -> {
                    double d1x = cur.x() - 2 * k.c1().x() + k.c2().x();
                    double d1y = cur.y() - 2 * k.c1().y() + k.c2().y();
                    double d2x = k.c1().x() - 2 * k.c2().x() + k.to().x();
                    double d2y = k.c1().y() - 2 * k.c2().y() + k.to().y();
                    double m = Math.max(Math.hypot(d1x, d1y), Math.hypot(d2x, d2y));
                    int n = Math.max(1, (int) Math.ceil(Math.sqrt(6 * m / (8 * TOLERANCE))));
                    Point prev = cur;
                    for (int i = 1; i <= n; i++) {
                        double t = (double) i / n, u = 1 - t;
                        double a = u * u * u, b = 3 * u * u * t, cc = 3 * u * t * t, d = t * t * t;
                        Point p = i == n ? k.to() : new Point(
                                a * cur.x() + b * k.c1().x() + cc * k.c2().x() + d * k.to().x(),
                                a * cur.y() + b * k.c1().y() + cc * k.c2().y() + d * k.to().y());
                        addEdge(edges, prev, p);
                        prev = p;
                    }
                    cur = k.to();
                }
            }
        }
        addEdge(edges, cur, c.start());
    }

    private static void addEdge(List<double[]> edges, Point a, Point b) {
        if (a.y() == b.y()) return;
        edges.add(new double[] {a.x(), a.y(), b.x(), b.y()});
    }
}
