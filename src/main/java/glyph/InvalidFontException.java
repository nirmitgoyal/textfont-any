package glyph;

import java.io.IOException;

public final class InvalidFontException extends IOException {
    public InvalidFontException(String msg) {
        super(msg);
    }

    public InvalidFontException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
