package hu.parking.reservation.api;

import hu.parking.reservation.service.ConflictException;
import hu.parking.reservation.service.InvalidRequestException;
import hu.parking.reservation.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled exception on {} {}", servletRequest.getMethod(), servletRequest.getRequestURI(), exception);
        }
        ErrorResponse errorResponse = errorResponse(statusCode, message(exception, statusCode), servletRequest.getRequestURI());
        return super.handleExceptionInternal(exception, errorResponse, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception,
                                                                      HttpHeaders headers, HttpStatusCode status,
                                                                      WebRequest request) {
        return ResponseEntity.status(status).headers(headers).build();
    }

    private static String message(Exception exception, HttpStatusCode statusCode) {
        if (exception instanceof HttpRequestMethodNotSupportedException) {
            return "HTTP method is not supported for this endpoint";
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            return "Content-Type must be application/json";
        }
        if (exception instanceof NoResourceFoundException) {
            return "Resource not found";
        }
        if (exception instanceof HttpMessageNotReadableException
                || exception instanceof TypeMismatchException
                || exception instanceof MissingServletRequestParameterException) {
            return "Malformed or invalid request value";
        }
        return reasonPhrase(statusCode);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatusCode status, String message, String path) {
        return ResponseEntity.status(status).body(errorResponse(status, message, path));
    }

    private static ErrorResponse errorResponse(HttpStatusCode status, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC), status.value(), reasonPhrase(status), message, path);
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
