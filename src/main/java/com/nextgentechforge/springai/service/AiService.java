package com.nextgentechforge.springai.service;

import com.nextgentechforge.springai.dto.ChatResponse;
import java.time.Clock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final ChatClient client;
    private final Clock clock;

    public AiService(ChatClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public ChatResponse chat(String message) {
        try {
            String answer = client.prompt().user(message).call().content();
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Empty provider response");
            }
            return new ChatResponse(answer, clock.instant());
        } catch (RuntimeException exception) {
            throw new AiUnavailableException(exception);
        }
    }
}
