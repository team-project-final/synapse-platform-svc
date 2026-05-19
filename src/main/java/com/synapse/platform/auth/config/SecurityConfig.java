package com.synapse.platform.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.config.JwtAuthenticationFilter;
import com.synapse.platform.auth.service.CustomOidcUserService;
import com.synapse.platform.auth.service.CustomOAuth2UserService;
import com.synapse.platform.auth.service.OAuth2FailureHandler;
import com.synapse.platform.auth.service.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            OAuth2FailureHandler oAuth2FailureHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2FailureHandler = oAuth2FailureHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository()))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (request, response, ex) ->
                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/**",
                                "/oauth2/**",
                                "/login/**",
                                "/api/v1/auth/callback",
                                "/api/v1/auth/refresh",
                                "/api/v1/billing/webhooks").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository(objectMapper);
    }

    @Bean
    public static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
