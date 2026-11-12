package com.boxcopilot.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String INTERNAL_DETAIL = "jdbc:postgresql://db:5432/boxpilot SQLSTATE 23505";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionReturnsGenericMessageWithoutDetails() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGenericException(new RuntimeException(INTERNAL_DETAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc")
                .doesNotContain("SQLSTATE");
    }

    @Test
    void unreadableBodyReturnsBadRequest() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException(INTERNAL_DETAIL, new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).doesNotContain("jdbc");
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleUnsupportedMethod(new HttpRequestMethodNotSupportedException("GET"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void authenticationExceptionReturnsGenericMessageWithoutDetails() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleAuthenticationException(new BadCredentialsException(INTERNAL_DETAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Authentication required")
                .doesNotContain("jdbc");
    }

    @Test
    void accessDeniedReturnsGenericMessageWithoutDetails() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleAccessDeniedException(new AccessDeniedException(INTERNAL_DETAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Access denied")
                .doesNotContain("jdbc");
    }

    @Test
    void fileStorageErrorReturnsGenericMessageWithoutPaths() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleFileStorageException(new FileStorageException("Cannot write /var/boxcopilot/images/x.png"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("File storage error")
                .doesNotContain("/var/boxcopilot");
    }

    @Test
    void rejectedUploadIsABadRequestWithTheUserMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleInvalidImage(new InvalidImageException("File is not a readable image"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("File is not a readable image");
    }

    @Test
    void validationErrorsKeepFieldNamesAndConstraintMessages() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(Object.class.getDeclaredMethod("toString"), -1), bindingResult);

        ResponseEntity<GlobalExceptionHandler.ValidationErrorResponse> response =
                handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getFieldErrors()).containsEntry("name", "must not be blank");
    }

    @Test
    void oversizedUploadReturnsPayloadTooLarge() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(15L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getMessage()).isEqualTo("Upload too large");
    }

    @Test
    void typeMismatchReturnsBadRequestWithoutEchoingTheValue() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("<script>", Long.class, "id", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid value for parameter 'id'");
    }

    @Test
    void busyUploadReturns503WithRetryAfter() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleUploadBusy(new UploadBusyException("internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).contains("try again").doesNotContain("internal");
    }
}
