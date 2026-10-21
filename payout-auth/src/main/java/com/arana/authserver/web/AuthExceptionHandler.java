package com.arana.authserver.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 problem details for auth errors. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ProblemDetail onAuth(AuthException ex) {
        HttpStatus status = ex.isBadRequest() ? HttpStatus.BAD_REQUEST : HttpStatus.UNAUTHORIZED;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
