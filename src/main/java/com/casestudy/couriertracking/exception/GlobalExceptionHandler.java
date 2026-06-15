package com.casestudy.couriertracking.exception;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_TYPE_PREFIX = "urn:courier-tracking:problem:";
    private static final String RETRY_AFTER_SECONDS = "1";
    private static final String DEFAULT_FIELD_ERROR_MESSAGE = "invalid";
    private static final String FIELD_ERROR_SEPARATOR = "; ";

    @ExceptionHandler(CourierNotFoundException.class)
    ProblemDetail handleCourierNotFound(CourierNotFoundException exception) {
        log.debug("Unknown courier queried: {}", exception.getCourierId());
        ProblemDetail problem = problemDetail(
                HttpStatus.NOT_FOUND, exception.getMessage(), "Courier Not Found", "courier-not-found");
        problem.setProperty("courierId", exception.getCourierId());
        return problem;
    }

    @ExceptionHandler(ImplausibleTimestampException.class)
    ProblemDetail handleImplausibleTimestamp(ImplausibleTimestampException exception) {
        log.debug("Implausible timestamp rejected", exception);
        return problemDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage(), "Implausible Timestamp", "implausible-timestamp");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleLockTimeout(PessimisticLockingFailureException exception) {
        log.warn("Lock wait could not be satisfied; advising client to retry", exception);
        ProblemDetail problem = problemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The request could not be processed due to temporary contention; please retry",
                "Temporary Contention", "temporary-contention");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                "Internal Server Error", "internal-error");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = exception.getBody();
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            String message = Objects.requireNonNullElse(
                    fieldError.getDefaultMessage(), DEFAULT_FIELD_ERROR_MESSAGE);
            errors.merge(fieldError.getField(), message,
                    (existing, next) -> existing + FIELD_ERROR_SEPARATOR + next);
        }
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static ProblemDetail problemDetail(
            HttpStatus status, String detail, String title, String typeSuffix) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + typeSuffix));
        return problem;
    }
}
