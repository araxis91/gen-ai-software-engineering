package com.homework2.support.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = extractBindingDetails(exception.getBindingResult());
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed.", request, details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = extractBindingDetails(exception.getBindingResult());
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed.", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception.getConstraintViolations().stream()
                .map(this::toApiErrorDetail)
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed.", request, details);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequestExceptions(
            Exception exception,
            HttpServletRequest request
    ) {
        if (exception instanceof HttpMessageNotReadableException) {
            return buildResponse(
                    HttpStatus.BAD_REQUEST,
                    "Malformed request body.",
                    request,
                    List.of(new ApiErrorDetail("body", rootCauseMessage(exception)))
            );
        }

        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            String parameterName = mismatchException.getName();
            String message = "Invalid value for parameter '" + parameterName + "'.";
            return buildResponse(
                    HttpStatus.BAD_REQUEST,
                    message,
                    request,
                    List.of(new ApiErrorDetail(parameterName, Objects.toString(mismatchException.getValue(), "null")))
            );
        }

        if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            String parameterName = missingParameterException.getParameterName();
            return buildResponse(
                    HttpStatus.BAD_REQUEST,
                    "Missing required request parameter.",
                    request,
                    List.of(new ApiErrorDetail(parameterName, "Parameter is required."))
            );
        }

        if (exception instanceof MissingServletRequestPartException missingPartException) {
            String partName = missingPartException.getRequestPartName();
            return buildResponse(
                    HttpStatus.BAD_REQUEST,
                    "Missing required request part.",
                    request,
                    List.of(new ApiErrorDetail(partName, "Request part is required."))
            );
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Bad request.", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        String message = "Unsupported media type.";
        List<ApiErrorDetail> details = List.of(
                new ApiErrorDetail("contentType", exception.getContentType() == null ? "Unknown" : exception.getContentType().toString())
        );
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, request, details);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = asHttpStatus(exception.getStatusCode());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return buildResponse(status, message, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        logger.error("Unhandled exception", exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error.",
                request,
                List.of(new ApiErrorDetail("cause", rootCauseMessage(exception)))
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ApiErrorDetail> details
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details == null || details.isEmpty() ? null : List.copyOf(details)
        );
        return ResponseEntity.status(status).body(body);
    }

    private List<ApiErrorDetail> extractBindingDetails(BindingResult bindingResult) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            details.add(new ApiErrorDetail(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "Invalid value." : fieldError.getDefaultMessage()
            ));
        }
        for (ObjectError objectError : bindingResult.getGlobalErrors()) {
            details.add(new ApiErrorDetail(
                    objectError.getObjectName(),
                    objectError.getDefaultMessage() == null ? "Invalid value." : objectError.getDefaultMessage()
            ));
        }
        return details;
    }

    private ApiErrorDetail toApiErrorDetail(ConstraintViolation<?> violation) {
        return new ApiErrorDetail(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        );
    }

    private HttpStatus asHttpStatus(HttpStatusCode statusCode) {
        if (statusCode instanceof HttpStatus httpStatus) {
            return httpStatus;
        }
        return HttpStatus.valueOf(statusCode.value());
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
