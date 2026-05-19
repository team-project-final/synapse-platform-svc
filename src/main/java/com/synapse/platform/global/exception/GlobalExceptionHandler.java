package com.synapse.platform.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatus());
        return ResponseEntity.status(status)
                .body(errorResponse(
                        exception.getErrorCode(),
                        status,
                        exception.getMessage(),
                        request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(errorResponse(
                        "PLAT-001",
                        status,
                        exception.getMessage(),
                        request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(errorResponse(
                        "PLAT-999",
                        status,
                        "Internal server error",
                        request));
    }

    private ErrorResponse errorResponse(
            String code,
            HttpStatus status,
            String detail,
            HttpServletRequest request) {
        return new ErrorResponse(
                "https://api.synapse.app/errors/" + code,
                status.getReasonPhrase(),
                status.value(),
                detail,
                code,
                traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId == null ? UUID.randomUUID().toString() : String.valueOf(traceId);
    }

    public record ErrorResponse(
            String type,
            String title,
            int status,
            String detail,
            String code,
            String traceId
    ) {
    }
}
