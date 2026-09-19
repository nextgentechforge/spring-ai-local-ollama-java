package com.nextgentechforge.springai.controller;

import com.nextgentechforge.springai.dto.WebSearchResponse;
import com.nextgentechforge.springai.service.WebSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web")
public class WebSearchController {
    private final WebSearchService search;
    public WebSearchController(WebSearchService search) { this.search = search; }

    @GetMapping("/search")
    public ResponseEntity<WebSearchResponse> search(@RequestParam String q) {
        var result = search.search(q);
        int status = switch (result.status()) {
            case INVALID_QUERY -> 400;
            case DISABLED, UNAVAILABLE -> 503;
            default -> 200;
        };
        return ResponseEntity.status(status).body(result);
    }
}
