package com.nextgentechforge.springai.controller;

import com.nextgentechforge.springai.dto.ChatRequest;
import com.nextgentechforge.springai.dto.ChatResponse;
import com.nextgentechforge.springai.service.AiService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final AiService service;

    public ChatController(AiService service) { this.service = service; }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(service.chat(request.message()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "application", "nextgentechforge-spring-ai"));
    }
}
