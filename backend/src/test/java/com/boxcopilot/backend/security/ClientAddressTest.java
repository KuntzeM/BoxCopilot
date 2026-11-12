package com.boxcopilot.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @ParameterizedTest
    @CsvSource({
            "203.0.113.7, 203.0.113.7",
            "::ffff:203.0.113.7, 203.0.113.7",
            "2001:db8:1:2:aaaa:bbbb:cccc:dddd, 20010db800010002::/64",
            "2001:db8:1:2:1111:2222:3333:4444, 20010db800010002::/64",
            "2001:db8:1:3::1, 20010db800010003::/64",
            "::1, 0000000000000000::/64"
    })
    void normalizes(String remote, String expected) {
        assertThat(ClientAddress.of(requestFrom(remote))).isEqualTo(expected);
    }

    @Test
    void emptyOrMissingBecomesUnknown() {
        assertThat(ClientAddress.of(requestFrom(""))).isEqualTo("unknown");
        assertThat(ClientAddress.of(null)).isEqualTo("unknown");
    }

    @Test
    void forwardedHeadersAreIgnored() {
        MockHttpServletRequest request = requestFrom("10.0.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");
    }

    private static MockHttpServletRequest requestFrom(String remote) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        return request;
    }
}
