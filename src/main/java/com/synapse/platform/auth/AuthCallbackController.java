package com.synapse.platform.auth;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthCallbackController {

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String error) {
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        return ResponseEntity.ok(Map.of("userId", userId));
    }
}
