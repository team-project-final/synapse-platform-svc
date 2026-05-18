package io.synapse.platform.auth.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public record OAuth2AuthorizationRequestDto(
        String authorizationUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        String authorizationGrantType
) {

    @JsonCreator
    public OAuth2AuthorizationRequestDto(
            @JsonProperty("authorizationUri") String authorizationUri,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("redirectUri") String redirectUri,
            @JsonProperty("scopes") Set<String> scopes,
            @JsonProperty("state") String state,
            @JsonProperty("additionalParameters") Map<String, Object> additionalParameters,
            @JsonProperty("authorizationGrantType") String authorizationGrantType) {
        this.authorizationUri = authorizationUri;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.state = state;
        this.additionalParameters = additionalParameters;
        this.authorizationGrantType = authorizationGrantType;
    }

    public static OAuth2AuthorizationRequestDto from(OAuth2AuthorizationRequest request) {
        return new OAuth2AuthorizationRequestDto(
                request.getAuthorizationUri(),
                request.getClientId(),
                request.getRedirectUri(),
                request.getScopes(),
                request.getState(),
                request.getAdditionalParameters(),
                request.getGrantType().getValue());
    }

    public OAuth2AuthorizationRequest toRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes)
                .state(state)
                .additionalParameters(additionalParameters)
                .build();
    }
}
