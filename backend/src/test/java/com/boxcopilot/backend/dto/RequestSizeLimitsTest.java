package com.boxcopilot.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void boxDescriptionIsCapped() {
        assertThat(validator.validate(new BoxRequestDTO("a", "b", "d".repeat(5000)))).isEmpty();
        assertThat(validator.validate(new BoxRequestDTO("a", "b", "d".repeat(5001)))).hasSize(1);
        assertThat(validator.validate(new BoxUpdateDTO("a", "b", "d".repeat(5001)))).hasSize(1);
    }

    @Test
    void bulkMoveItemIdsAreCapped() {
        assertThat(validator.validate(new BulkMoveItemsDTO(Collections.nCopies(500, 1L), 1L))).isEmpty();
        assertThat(validator.validate(new BulkMoveItemsDTO(Collections.nCopies(501, 1L), 1L))).hasSize(1);
    }
}
