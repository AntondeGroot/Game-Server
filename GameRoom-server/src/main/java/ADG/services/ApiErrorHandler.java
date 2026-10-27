package ADG.services;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a path or query parameter that breaks a constraint in gameroom_openapi.yaml (such as a
 * negative picture number) into a 400 problem, instead of the 500 Spring gives by default.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolated(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
