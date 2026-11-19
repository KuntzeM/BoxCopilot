package com.boxcopilot.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void stripsLineBreaksAndControlCharacters() {
        assertThat(LogSanitizer.clean("a\r\nINFO forged\tx y")).isEqualTo("a__INFO forged_x_y");
        assertThat(LogSanitizer.clean(null)).isNull();
    }
}
