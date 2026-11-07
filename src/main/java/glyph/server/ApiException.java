package glyph.server;

import java.util.Map;

/** Error with an HTTP status and a stable machine-readable code. */
final class ApiException extends RuntimeException {
    final int status;
    final String code;
    final Map<String, String> headers;

    ApiException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    ApiException(int status, String code, String message, Map<String, String> headers) {
        super(message);
        this.status = status;
        this.code = code;
        this.headers = headers;
    }
}
