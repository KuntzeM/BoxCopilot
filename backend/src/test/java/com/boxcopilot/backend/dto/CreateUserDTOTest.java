package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.domain.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateUserDTO withPassword(String password) {
        CreateUserDTO dto = new CreateUserDTO("bob", "Bob", null, Role.USER);
        dto.setPassword(password); // the setter is what JSON binding uses
        return dto;
    }

    @Test
    void blankPasswordMeansNoPassword() {
        for (String blank : new String[]{"", "   ", null}) {
            CreateUserDTO dto = withPassword(blank);
            assertThat(dto.getPassword()).isNull();
            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Test
    void nonBlankPasswordKeepsTheLengthRule() {
        assertThat(validator.validate(withPassword("short"))).hasSize(1);
        assertThat(validator.validate(withPassword("a-long-enough-pw"))).isEmpty();
    }
}
