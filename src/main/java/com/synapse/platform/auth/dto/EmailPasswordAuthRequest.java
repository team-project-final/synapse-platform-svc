package com.synapse.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailPasswordAuthRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
        String password
) {
}
