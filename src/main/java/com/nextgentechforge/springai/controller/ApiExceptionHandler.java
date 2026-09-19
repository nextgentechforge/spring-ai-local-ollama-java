package com.nextgentechforge.springai.controller;

import com.nextgentechforge.springai.service.AiUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AiUnavailableException.class)
    public ProblemDetail unavailable(AiUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI provider could not complete this request. Please retry later.");
    }
}
