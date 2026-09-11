package com.fuelpulse.api.exception;

import com.fuelpulse.api.service.NearbyStationService.RadiusExceededException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Translates exceptions into RFC 7807 problem details.
 *
 * <p>The governing rule: a response tells the caller what <em>they</em> can fix and
 * nothing about how the service is built. Validation failures name the offending
 * parameter, because the caller owns that mistake. Everything unrecognised becomes
 * a bare 500 — the detail goes to the log, where operators can read it and
 * attackers cannot.
 *
 * <p>This complements {@code server.error.include-message: never}: that setting
 * covers responses Spring generates, this covers the ones the application does.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    // Method parameter paths arrive as "nearby.lat"; keep the parameter only.
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return field + ": " + v.getMessage();
                })
                .sorted()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request parameters");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(RadiusExceededException.class)
    public ProblemDetail handleRadiusExceeded(RadiusExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Radius too large");
        problem.setDetail(ex.getMessage());
        problem.setProperty("requestedMeters", ex.getRequested());
        problem.setProperty("maximumMeters", ex.getMaximum());
        return problem;
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail handleMalformedParameters(Exception ex) {
        // The exception message can echo the submitted value; the caller gets the
        // shape of the problem, not their own input reflected back.
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request parameters");
        problem.setDetail("One or more query parameters are missing or of the wrong type");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal server error");
        return problem;
    }
}