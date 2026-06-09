package com.synapse.platform.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import com.synapse.platform.user.dto.request.UserProfileUpdateRequest;
import com.synapse.platform.user.dto.response.UserProfileResponse;
import com.synapse.platform.user.service.UserService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class UserControllerTest {

    private final UserService userService = org.mockito.Mockito.mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getMe_shouldReturnProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userService.getMyProfile(userId)).willReturn(new UserProfileResponse(
                userId,
                "user@example.com",
                "User",
                "https://example.com/avatar.png",
                "ko-KR",
                true));

        mockMvc.perform(get("/api/v1/users/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("User"))
                .andExpect(jsonPath("$.language").value("ko-KR"))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void updateMe_shouldDelegateProfileUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("Updated", "en-US");
        given(userService.updateMyProfile(userId, request)).willReturn(new UserProfileResponse(
                userId,
                "user@example.com",
                "Updated",
                null,
                "en-US",
                true));

        mockMvc.perform(put("/api/v1/users/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated"))
                .andExpect(jsonPath("$.language").value("en-US"));

        verify(userService).updateMyProfile(userId, request);
    }

    @Test
    void updateMe_unsupportedLanguage_shouldReturnBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/users/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "Updated",
                                "language", "한국어"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_shouldDelegatePasswordChange() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/users/me/password")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Oldpass1!",
                                "newPassword", "Newpass1!"))))
                .andExpect(status().isNoContent());

        verify(userService).changeMyPassword(userId, "Oldpass1!", "Newpass1!");
    }

    @Test
    void deleteMe_shouldDelegateAccountDelete() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/me")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNoContent());

        verify(userService).deleteMyAccount(userId);
    }

    @Test
    void getMe_malformedPrincipal_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .principal(new TestingAuthenticationToken("not-a-uuid", null)))
                .andExpect(status().isUnauthorized());
    }
}
