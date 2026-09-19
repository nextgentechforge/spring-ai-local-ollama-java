package com.nextgentechforge.springai.dto;

import java.time.Instant;

public record ChatResponse(String answer, Instant timestamp) { }
