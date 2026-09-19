package com.nextgentechforge.springai.dto;

import java.time.Instant;
import java.util.List;

public record WebSearchResponse(String query, Status status, String note, Instant fetchedAt,
                                List<Source> sources) {
    public enum Status { OK, NO_RESULTS, UNAVAILABLE, DISABLED, INVALID_QUERY }
    public record Source(String title, String url, String snippet) { }
}
