package com.synapse.platform.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
            EntityNotFoundException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(errorResponse("PLAT-404", status, exception.getMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(errorResponse("PLAT-403", status, exception.getMessage(), request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(errorResponse("PLAT-" + status.value(), status, exception.getReason(), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(errorResponse("PLAT-001", status, "Invalid request body", request));
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
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
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
