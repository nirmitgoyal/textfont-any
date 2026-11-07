package glyph;

/** Requested canvas exceeds the pixel budget. Subclass of IAE so existing callers keep working. */
public final class CanvasTooLargeException extends IllegalArgumentException {
    public CanvasTooLargeException(String msg) {
        super(msg);
    }
}
