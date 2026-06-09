package com.synapse.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "ko-KR|en-US|ja-JP")
        String language
) {
}
