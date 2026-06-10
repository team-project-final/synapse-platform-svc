package com.synapse.platform.auth.controller;

import com.synapse.platform.auth.dto.OAuthConnectionResponse;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.service.OAuthConnectionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/oauth")
public class OAuthConnectionController {

    private final OAuthConnectionService oauthConnectionService;

    public OAuthConnectionController(OAuthConnectionService oauthConnectionService) {
        this.oauthConnectionService = oauthConnectionService;
    }

    @GetMapping
    public List<OAuthConnectionResponse> list(Authentication authentication) {
        return oauthConnectionService.listConnections(currentUserId(authentication));
    }

    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(Authentication authentication, @PathVariable String provider) {
        oauthConnectionService.unlink(currentUserId(authentication), provider);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedTokenException("Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedTokenException("Authentication required");
        }
    }
}
