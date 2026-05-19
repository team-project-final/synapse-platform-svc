package com.synapse.platform.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void handleBusinessException_shouldReturnRfc7807Response() throws Exception {
        // When & Then
        mockMvc.perform(get("/business-error").requestAttr("traceId", "trace-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.synapse.app/errors/PLAT-001"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("oauth failed"))
                .andExpect(jsonPath("$.code").value("PLAT-001"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    void handleValidationException_shouldReturnRfc7807BadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/validation-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.synapse.app/errors/PLAT-001"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PLAT-001"));
    }

    @Test
    void handleUnexpectedException_shouldReturnRfc7807InternalServerError() throws Exception {
        // When & Then
        mockMvc.perform(get("/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://api.synapse.app/errors/PLAT-999"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("PLAT-999"));
    }

    @Controller
    static class TestController {

        @GetMapping("/business-error")
        void businessError() {
            throw new TestBusinessException("oauth failed");
        }

        @GetMapping("/validation-error")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void validationError(@Valid @ModelAttribute ValidationRequest request) {
        }

        @GetMapping("/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("unexpected");
        }
    }

    static class TestBusinessException extends BusinessException {

        TestBusinessException(String message) {
            super("PLAT-001", 400, message);
        }
    }

    static class ValidationRequest {

        @NotBlank
        private String name;

        public String getName() {
            return name;
        }
    }
}
