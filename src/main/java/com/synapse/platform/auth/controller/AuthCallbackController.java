package com.synapse.platform.auth.controller;

import com.synapse.platform.auth.exception.OAuthProcessingException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthCallbackController {

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String error) {
        if (error != null) {
            throw new OAuthProcessingException(error);
        }
        if (userId == null) {
            throw new OAuthProcessingException("userId is required");
        }
        return ResponseEntity.ok(Map.of("userId", userId));
    }
}
