package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for redeeming a magic login token.
 */
public record MagicLoginRequestDTO(@NotBlank @Size(max = 200) String token) {
}
