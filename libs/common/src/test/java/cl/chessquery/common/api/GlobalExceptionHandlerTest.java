package cl.chessquery.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsStatusAndCode() {
        ResponseEntity<ErrorResponse> r = handler.handleApi(ApiException.conflict("PLAN_LIMIT_REACHED", "límite"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().error()).isEqualTo("PLAN_LIMIT_REACHED");
        assertThat(r.getBody().message()).isEqualTo("límite");
        assertThat(r.getBody().timestamp()).isNotNull();
    }

    @Test
    void factoriesMapToExpectedStatuses() {
        assertThat(ApiException.notFound("X", "m").getStatus()).isEqualTo(404);
        assertThat(ApiException.forbidden("X", "m").getStatus()).isEqualTo(403);
        assertThat(ApiException.badRequest("X", "m").getStatus()).isEqualTo(400);
        assertThat(new ApiException(HttpStatus.GONE, "X", "m").getStatus()).isEqualTo(410);
    }

    @Test
    void genericExceptionNeverLeaksDetails() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneric(new IllegalStateException("secreto interno"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().message()).doesNotContain("secreto");
        assertThat(r.getBody().error()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void validationErrorsListFields() throws Exception {
        var target = new Object() { @SuppressWarnings("unused") String name; };
        var binding = new org.springframework.validation.BeanPropertyBindingResult(target, "req");
        binding.addError(new org.springframework.validation.FieldError("req", "name", "no debe estar vacío"));
        var param = new org.springframework.core.MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationErrorsListFields"), -1);
        var ex = new org.springframework.web.bind.MethodArgumentNotValidException(param, binding);
        ResponseEntity<ErrorResponse> r = handler.handleValidation(ex);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(r.getBody().message()).contains("name: no debe estar vacío");
    }

    @Test
    void notFoundAndForbiddenAndBadInput() {
        assertThat(handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/x")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("no")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleBadInput(new IllegalArgumentException("x")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cambiosConcurrentesSon409() {
        ResponseEntity<ErrorResponse> r = handler.handleConcurrentChange(
                new org.springframework.dao.OptimisticLockingFailureException("version"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().error()).isEqualTo("CONCURRENT_UPDATE");
        assertThat(handler.handleConcurrentChange(new org.springframework.dao.DataIntegrityViolationException("dup"))
                .getStatusCode().value()).isEqualTo(409);
    }
}
