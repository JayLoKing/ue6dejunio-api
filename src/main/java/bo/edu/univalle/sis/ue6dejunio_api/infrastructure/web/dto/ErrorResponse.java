package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Map<String, String>> details) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse withDetails(
            int status,
            String error,
            String message,
            String path,
            List<Map<String, String>> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
