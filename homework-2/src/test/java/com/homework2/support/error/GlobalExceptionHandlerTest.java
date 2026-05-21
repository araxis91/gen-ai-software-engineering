package com.homework2.support.error;

import com.homework2.support.api.dto.TicketRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMethodArgumentNotValidReturnsValidationErrorDetails() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "ticketRequest");
        bindingResult.addError(new FieldError("ticketRequest", "customerEmail", "must be a well-formed email address"));

        Method method = SampleController.class.getDeclaredMethod("handleCreate", TicketRequest.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentNotValid(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Validation failed.");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("customerEmail");
    }

    @Test
    void handleBindExceptionIncludesFieldAndGlobalErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "ticket");
        bindingResult.addError(new FieldError("ticket", "subject", "must not be blank"));
        bindingResult.addError(new ObjectError("ticket", "global validation error"));
        BindException exception = new BindException(bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleBindException(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).hasSize(2);
        assertThat(response.getBody().details()).extracting(ApiErrorDetail::field)
                .containsExactlyInAnyOrder("subject", "ticket");
    }

    @Test
    void handleConstraintViolationReturnsDetails() {
        ConstraintViolation<Object> first = violation("ticket.customerId", "must not be blank");
        ConstraintViolation<Object> second = violation("ticket.metadata.browser", "must not be blank");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(first, second));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).hasSize(2);
    }

    @Test
    void handleBadRequestExceptionsForMalformedBody() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Malformed JSON",
                new IllegalStateException("unexpected token")
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequestExceptions(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Malformed request body.");
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("body");
    }

    @Test
    void handleBadRequestExceptionsForTypeMismatch() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc",
                Integer.class,
                "page",
                null,
                null
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequestExceptions(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid value for parameter 'page'.");
        assertThat(response.getBody().details().getFirst().message()).isEqualTo("abc");
    }

    @Test
    void handleBadRequestExceptionsForMissingParameter() {
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("priority", "String");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequestExceptions(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Missing required request parameter.");
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("priority");
    }

    @Test
    void handleBadRequestExceptionsForMissingPart() {
        MissingServletRequestPartException exception = new MissingServletRequestPartException("file");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequestExceptions(exception, request("/tickets/import"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Missing required request part.");
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("file");
    }

    @Test
    void handleBadRequestExceptionsFallbackResponse() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequestExceptions(
                new IllegalArgumentException("bad"),
                request("/tickets")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Bad request.");
    }

    @Test
    void handleUnsupportedMediaTypeIncludesContentTypeDetail() throws Exception {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN,
                List.of(MediaType.APPLICATION_JSON)
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupportedMediaType(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("contentType");
        assertThat(response.getBody().details().getFirst().message()).isEqualTo(MediaType.TEXT_PLAIN.toString());
    }

    @Test
    void handleResponseStatusExceptionUsesProvidedReason() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(exception, request("/tickets/1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Ticket not found");
    }

    @Test
    void handleResponseStatusExceptionFallsBackToReasonPhraseWhenReasonMissing() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Bad Request");
    }

    @Test
    void handleUnexpectedExceptionReturns500WithRootCauseDetail() {
        Exception exception = new Exception("outer", new IllegalStateException());

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(exception, request("/tickets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error.");
        assertThat(response.getBody().details().getFirst().field()).isEqualTo("cause");
        assertThat(response.getBody().details().getFirst().message()).isEqualTo("IllegalStateException");
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(uri);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return request;
    }

    private static ConstraintViolation<Object> violation(String field, String message) {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(field);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    private static class SampleController {
        @SuppressWarnings("unused")
        public void handleCreate(TicketRequest request) {
        }
    }
}
