package br.com.manager.exception;

import org.springframework.http.HttpStatus;

/** An application error whose message is safe to return to the client. */
public class DefaultErrorException extends RuntimeException {

    private final HttpStatus status;

    public DefaultErrorException(HttpStatus status, String message) {
        super(message);
        if (!status.isError()) throw new IllegalArgumentException("An error HTTP status is required.");
        this.status = status;
    }

    public HttpStatus getStatusCode() {
        return status;
    }
}
