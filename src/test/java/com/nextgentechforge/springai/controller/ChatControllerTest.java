package com.nextgentechforge.springai.controller;

import com.nextgentechforge.springai.dto.ChatResponse;
import com.nextgentechforge.springai.service.AiService;
import com.nextgentechforge.springai.service.AiUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest {
    private AiService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(AiService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void returnsAnswerAndTimestamp() throws Exception {
        when(service.chat("hello")).thenReturn(new ChatResponse("Hello", Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Hello"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T00:00:00Z"));
        verify(service).chat("hello");
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"   \"}", "{bad"})
    void rejectsInvalidInput(String body) throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsOversizedInput() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + "x".repeat(4001) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void healthDoesNotCallAi() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(service);
    }
    @Test void providerFailureDoesNotLeakDetails() throws Exception {
        when(service.chat("hello")).thenThrow(new AiUnavailableException(new RuntimeException("private-provider-detail")));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(
                        "The AI provider could not complete this request. Please retry later."));
    }
}
