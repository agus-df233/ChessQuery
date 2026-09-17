package cl.chessquery.common.api;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con código estable para el frontend.
 * Contrato: {@code { status, error, message, timestamp }} (docs/CONTEXT.md).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public ApiException(HttpStatus status, String error, String message) {
        this(status.value(), error, message);
    }

    public static ApiException notFound(String error, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, error, message);
    }

    public static ApiException forbidden(String error, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, error, message);
    }

    public static ApiException conflict(String error, String message) {
        return new ApiException(HttpStatus.CONFLICT, error, message);
    }

    public static ApiException badRequest(String error, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, error, message);
    }

    public int getStatus() { return status; }

    public String getError() { return error; }
}
