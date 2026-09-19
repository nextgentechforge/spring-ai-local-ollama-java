package com.nextgentechforge.springai.service;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiServiceTest {
    private final ChatModel model = mock(ChatModel.class);
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final AiService service = new AiService(ChatClient.builder(model).build(), Clock.fixed(now, ZoneOffset.UTC));
    @org.junit.jupiter.api.BeforeEach void setupOptions() { when(model.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build()); }
    @Test void usesChatClientAndTimestamp() {
        when(model.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage("Dependency injection supplies collaborators.")))));
        var response = service.chat("Explain dependency injection");
        assertThat(response.answer()).contains("collaborators");
        assertThat(response.timestamp()).isEqualTo(now);
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText()).isEqualTo("Explain dependency injection");
    }
    @Test void wrapsProviderFailure() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider failed"));
        assertThatThrownBy(() -> service.chat("hello")).isInstanceOf(AiUnavailableException.class);
    }
    @Test void rejectsEmptyAnswer() {
        when(model.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage("")))));
        assertThatThrownBy(() -> service.chat("hello")).isInstanceOf(AiUnavailableException.class);
    }
}
