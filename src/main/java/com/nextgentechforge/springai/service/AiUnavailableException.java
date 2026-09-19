package com.nextgentechforge.springai.service;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(Throwable cause) { super("AI provider unavailable", cause); }
}
