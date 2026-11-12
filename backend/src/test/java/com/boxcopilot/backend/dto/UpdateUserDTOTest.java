package com.boxcopilot.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdateUserDTO dto(String username, String name) {
        return new UpdateUserDTO(username, name, null, null);
    }

    @Test
    void nullFieldsMeanUnchanged() {
        assertThat(validator.validate(dto(null, null))).isEmpty();
    }

    @Test
    void blankUsernameIsRejected() {
        assertThat(validator.validate(dto("", null))).isNotEmpty();
        assertThat(validator.validate(dto("   ", null))).isNotEmpty();
        assertThat(validator.validate(dto("bob", "Bob"))).isEmpty();
    }

    @Test
    void lengthsAreLimitedToTheColumnSize() {
        assertThat(validator.validate(dto("a".repeat(255), "b".repeat(255)))).isEmpty();
        assertThat(validator.validate(dto("a".repeat(256), null))).hasSize(1);
        assertThat(validator.validate(dto(null, "b".repeat(256)))).hasSize(1);
    }
}
