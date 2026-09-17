package cl.chessquery.common.api;

import java.time.Instant;

/** Cuerpo de error único de la plataforma. */
public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
