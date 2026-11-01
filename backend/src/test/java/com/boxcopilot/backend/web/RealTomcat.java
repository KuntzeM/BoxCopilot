package com.boxcopilot.backend.web;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared pieces for tests that talk to the application over a real socket (real Tomcat, real filter chain). */
final class RealTomcat {

    private RealTomcat() {
    }

    /** What the servlet container hands the first filter after its own proxy handling: peer address and scheme. */
    record Seen(String remoteAddr, String scheme, boolean secure) {
    }

    /** No remote OIDC discovery while the context starts (same pattern as McpIntegrationTestBase). */
    @TestConfiguration(proxyBeanMethods = false)
    static class Support {

        static final AtomicReference<Seen> LAST = new AtomicReference<>();

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost/authorize").tokenUri("http://localhost/token")
                    .jwkSetUri("http://localhost/jwks").issuerUri("http://localhost")
                    .userNameAttributeName(IdTokenClaimNames.SUB).build());
        }

        @Bean
        FilterRegistrationBean<jakarta.servlet.Filter> peerRecorder() {
            FilterRegistrationBean<jakarta.servlet.Filter> bean = new FilterRegistrationBean<>((req, res, chain) -> {
                LAST.set(new Seen(req.getRemoteAddr(), req.getScheme(), req.isSecure()));
                chain.doFilter(req, res);
            });
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return bean;
        }
    }

    /** Copies the two proxy settings out of application-prod.yml, placeholder default included. */
    static class ProdProxySettings implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        private final Map<String, Object> operator;

        ProdProxySettings(Map<String, Object> operator) {
            this.operator = operator;
        }

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                        .load("application-prod", new ClassPathResource("application-prod.yml"));
                StandardEnvironment resolver = new StandardEnvironment();
                yaml.forEach(resolver.getPropertySources()::addFirst);
                resolver.getPropertySources().addFirst(new MapPropertySource("operator", operator));
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("prod-proxy", Map.of(
                        "server.forward-headers-strategy", resolver.getProperty("server.forward-headers-strategy"),
                        "server.tomcat.remoteip.internal-proxies",
                        resolver.getProperty("server.tomcat.remoteip.internal-proxies"))));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class DefaultPattern extends ProdProxySettings {
        DefaultPattern() {
            super(Map.of());
        }
    }

    static class NarrowedPattern extends ProdProxySettings {
        NarrowedPattern() {
            super(Map.of("TRUSTED_PROXY_PATTERN", "172\\.18\\.0\\.2"));
        }
    }

    record Reply(int status, String body, java.net.http.HttpHeaders headers) {
    }

    /** Cookie-keeping client with the SPA's CSRF handling. */
    static final class Client {
        private final String base;
        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        private final int port;

        Client(int port) {
            this.port = port;
            this.base = "http://127.0.0.1:" + port;
        }

        Reply send(String method, String path, String contentType, byte[] body, String... headers) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            if (contentType != null) {
                b.header("Content-Type", contentType);
            }
            for (int i = 0; i < headers.length; i += 2) {
                b.header(headers[i], headers[i + 1]);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.body(), r.headers());
        }

        String csrf() throws Exception {
            String json = send("GET", "/api/v1/csrf", null, null).body();
            Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (!m.find()) {
                throw new IllegalStateException("no csrf token in " + json);
            }
            return m.group(1);
        }

        /** Unsafe request with the current CSRF token. */
        Reply mutate(String method, String path, String contentType, byte[] body) throws Exception {
            return send(method, path, contentType, body, "X-XSRF-TOKEN", csrf());
        }

        Reply json(String method, String path, String json) throws Exception {
            return mutate(method, path, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        void login(String user, String password) throws Exception {
            Reply r = mutate("POST", "/api/auth/login", "application/x-www-form-urlencoded",
                    ("username=" + user + "&password=" + password).getBytes(StandardCharsets.UTF_8));
            if (r.status() != 200) {
                throw new IllegalStateException("login failed: " + r.status() + " " + r.body());
            }
        }

        static long idOf(String json) {
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
            if (!m.find()) {
                throw new IllegalStateException("no id in " + json);
            }
            return Long.parseLong(m.group(1));
        }

        private record Multipart(String boundary, byte[] body) {
        }

        private static Multipart multipart(String partName, String filename, String partType, byte[] content) {
            String boundary = "----bp" + System.nanoTime();
            byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + partName
                    + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + partType + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] all = new byte[head.length + content.length + tail.length];
            System.arraycopy(head, 0, all, 0, head.length);
            System.arraycopy(content, 0, all, head.length, content.length);
            System.arraycopy(tail, 0, all, head.length + content.length, tail.length);
            return new Multipart(boundary, all);
        }

        /** multipart/form-data with one part. */
        Reply upload(String path, String partName, String filename, String partType, byte[] content) throws Exception {
            Multipart m = multipart(partName, filename, partType, content);
            return mutate("POST", path, "multipart/form-data; boundary=" + m.boundary(), m.body());
        }

        /**
         * Same upload over a raw socket: the response is read by a thread that starts before the body is written,
         * because Tomcat answers a refused upload while the client is still sending and then closes the
         * connection (the JDK client loses such a response).
         */
        Reply uploadRaw(String path, String partName, String filename, String partType, byte[] content) throws Exception {
            String token = csrf();
            String cookie = cookies.getCookieStore().getCookies().stream()
                    .map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "));
            Multipart m = multipart(partName, filename, partType, content);
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                java.io.ByteArrayOutputStream received = new java.io.ByteArrayOutputStream();
                Thread reader = new Thread(() -> {
                    byte[] buf = new byte[8192];
                    try {
                        for (int n; (n = socket.getInputStream().read(buf)) > 0; ) {
                            received.write(buf, 0, n);
                        }
                    } catch (IOException ignored) {
                        // reset after the server stopped listening: what arrived before it is the answer
                    }
                });
                reader.start();
                try {
                    java.io.OutputStream out = socket.getOutputStream();
                    out.write(("POST " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nCookie: " + cookie
                            + "\r\nX-XSRF-TOKEN: " + token + "\r\nContent-Type: multipart/form-data; boundary="
                            + m.boundary() + "\r\nContent-Length: " + m.body().length + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.write(m.body());
                    out.flush();
                } catch (IOException refusedWhileSending) {
                    // the server closed early: the answer is whatever the reader got
                }
                reader.join(10_000);
                String raw = received.toString(StandardCharsets.UTF_8);
                int split = raw.indexOf("\r\n\r\n");
                String headerBlock = split < 0 ? raw : raw.substring(0, split);
                int status = Integer.parseInt(headerBlock.split(" ", 3)[1]);
                return new Reply(status, split < 0 ? "" : raw.substring(split + 4), null);
            }
        }
    }
}
