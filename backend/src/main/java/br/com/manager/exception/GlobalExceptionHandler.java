package br.com.manager.exception;

import br.com.manager.models.responses.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DefaultErrorException.class)
    public ResponseEntity<Object> applicationError(DefaultErrorException exception) {
        return response(exception.getStatusCode(), exception.getMessage(), new HttpHeaders());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> validationError(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST, "Confira os campos e tente novamente.", new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        // Framework messages may contain rejected values, paths or implementation details.
        if (status.is5xxServerError()) LOG.error("Falha interna ao processar requisição.", exception);
        return response(status, message(status.value()), headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpectedError(Exception exception) {
        LOG.error("Falha inesperada ao processar requisição.", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, message(500), new HttpHeaders());
    }

    private ResponseEntity<Object> response(HttpStatusCode status, String message, HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(new ErrorResponse(message), headers, status);
    }

    private String message(int status) {
        return switch (status) {
            case 400 -> "Confira os campos e tente novamente.";
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Método não permitido.";
            case 406 -> "Formato de resposta não disponível.";
            case 413 -> "Requisição muito grande.";
            case 415 -> "Formato de requisição não suportado.";
            default -> status >= 500
                ? "Não foi possível processar a solicitação. Tente novamente."
                : "Requisição inválida.";
        };
    }
}
