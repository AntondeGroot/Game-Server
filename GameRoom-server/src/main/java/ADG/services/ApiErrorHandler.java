package ADG.services;

import ADG.generated.api.ChatApi;
import ADG.generated.api.GamesApi;
import ADG.generated.api.PlayersApi;
import ADG.generated.api.RoomsApi;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Sends errors of the controllers that implement gameroom_openapi.yaml as
 * {@code application/problem+json}, as the spec promises: a {@code ResponseStatusException}
 * from {@link RoomManager}, an invalid request body, or a parameter that breaks a constraint
 * (such as a negative picture number, which would otherwise be a 500).
 */
@RestControllerAdvice(assignableTypes = {RoomsApi.class, PlayersApi.class, GamesApi.class, ChatApi.class})
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolated(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
