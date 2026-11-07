package glyph;

public final class Bitmap {
    private final int width;
    private final int height;
    private final boolean[] ink;

    public Bitmap(int width, int height) {
        this.width = width;
        this.height = height;
        this.ink = new boolean[width * height];
    }

    public int width() { return width; }

    public int height() { return height; }

    public boolean get(int x, int y) { return ink[y * width + x]; }

    public void set(int x, int y) { ink[y * width + x] = true; }

    public int inkCount() {
        int n = 0;
        for (boolean b : ink) if (b) n++;
        return n;
    }
}
