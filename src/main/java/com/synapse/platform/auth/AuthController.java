package com.synapse.platform.auth;

import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.jwt.JwtTokenProvider;
import com.synapse.platform.auth.jwt.RefreshTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedTokenException("Invalid refresh token");
        }

        UUID userId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new UnauthorizedTokenException("Refresh token does not match stored token");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, AuthRoles.DEFAULT_USER_ROLES);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.rotate(userId, newRefreshToken);
        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }

    public record TokenRefreshRequest(
            @NotBlank
            String refreshToken
    ) {
    }

    public record TokenRefreshResponse(
            String accessToken,
            String refreshToken
    ) {
    }
}
