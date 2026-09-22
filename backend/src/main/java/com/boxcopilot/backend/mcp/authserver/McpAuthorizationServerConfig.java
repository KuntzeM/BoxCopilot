package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.McpUrls;
import com.boxcopilot.backend.mcp.McpUserGate;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.boxcopilot.backend.mcp.web.McpPendingCookies;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * The OAuth 2.1 authorization server BoxPilot runs for its MCP endpoint.
 *
 * <p>Its {@code securityMatcher} covers only {@link McpAuthorizationServerEndpoints}. Every other endpoint Spring
 * Authorization Server would publish — introspection, device authorization, pushed authorization requests, JWKS
 * and all of OpenID Connect — is outside the matcher, so no authorization server filter ever sees such a request
 * and it falls through to the application chain, which requires a session and finds no handler.
 *
 * <p>The chain is session based, because the resource owner authenticates with the SPA's session, CSRF is on and
 * every response is {@code X-Frame-Options: DENY}. It creates no session of its own: the request cache is
 * disabled and an anonymous authorize request is answered from the entry point with a cookie and a redirect.
 *
 * <p>Access tokens are opaque references, so the server needs no signing key: with no {@code JwtEncoder} and no
 * {@code JWKSource} bean, {@code OAuth2ConfigurerUtils} builds a token generator out of
 * {@code OAuth2AccessTokenGenerator} and {@code OAuth2RefreshTokenGenerator} alone. No unused in-memory key is
 * created, and {@code /oauth2/jwks} is neither published nor reachable.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpAuthorizationServerConfig {

    /** Metadata entries for endpoints and grants BoxPilot does not serve; advertising them would be a lie. */
    private static final List<String> UNSUPPORTED_METADATA = List.of(
            OAuth2AuthorizationServerMetadataClaimNames.PUSHED_AUTHORIZATION_REQUEST_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.DEVICE_AUTHORIZATION_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT_AUTH_METHODS_SUPPORTED,
            OAuth2AuthorizationServerMetadataClaimNames.JWKS_URI,
            OAuth2AuthorizationServerMetadataClaimNames.REGISTRATION_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.TLS_CLIENT_CERTIFICATE_BOUND_ACCESS_TOKENS,
            OAuth2AuthorizationServerMetadataClaimNames.DPOP_SIGNING_ALG_VALUES_SUPPORTED);

    // The metadata builder validates that these claims are lists, so a set would fail at request time.
    private static final List<String> SUPPORTED_GRANT_TYPES = List.of(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue());

    private static final List<String> SUPPORTED_CLIENT_AUTHENTICATION =
            List.of(ClientAuthenticationMethod.NONE.getValue());

    private static final List<String> SUPPORTED_SCOPES = List.of(McpOAuth2Attributes.SCOPE_READ);

    /**
     * The issuer never comes from the request or from an {@code X-Forwarded-*} header: it is the configured
     * backend URL, so a forged host header cannot move the advertised endpoints anywhere else.
     */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(McpProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.backendUrl()).build();
    }

    @Bean
    OAuth2AuthorizationConsentService mcpAuthorizationConsentService() {
        return new McpNoOpAuthorizationConsentService();
    }

    @Bean
    @Order(1)
    SecurityFilterChain mcpAuthorizationServerFilterChain(HttpSecurity http, McpProperties properties,
                                                          RegisteredClientRepository registeredClientRepository,
                                                          McpSessionUserResolver userResolver, McpUserGate userGate,
                                                          McpPendingCookies pendingCookies,
                                                          ObjectProvider<SecurityContextRepository> contextRepository,
                                                          @Value("${frontend.url}") String frontendUrl)
            throws Exception {
        RequestMatcher authorizeMatcher = matcher(McpAuthorizationServerEndpoints.AUTHORIZE);
        RequestMatcher consentSubmission =
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
                        McpAuthorizationServerEndpoints.AUTHORIZE);
        CsrfTokenRequestHandler csrfRequestHandler = new XorCsrfTokenRequestAttributeHandler();
        SecurityContextRepository securityContextRepository =
                contextRepository.getIfAvailable(HttpSessionSecurityContextRepository::new);

        http
                .securityMatcher(McpAuthorizationServerEndpoints.AUTHORIZE, McpAuthorizationServerEndpoints.CONSENT,
                        McpAuthorizationServerEndpoints.TOKEN, McpAuthorizationServerEndpoints.REVOKE,
                        McpAuthorizationServerEndpoints.METADATA)
                .with(OAuth2AuthorizationServerConfigurer.authorizationServer(), server -> server
                        // Opaque reference tokens need no signing key; the refresh generator is ours so that a
                        // public client gets a rotating refresh token at all.
                        .tokenGenerator(new DelegatingOAuth2TokenGenerator(
                                new OAuth2AccessTokenGenerator(), new McpRefreshTokenGenerator()))
                        .authorizationServerMetadataEndpoint(metadata -> metadata
                                .authorizationServerMetadataCustomizer(builder -> builder.claims(
                                        McpAuthorizationServerConfig::restrictMetadataToWhatIsServed)))
                        .authorizationEndpoint(endpoint -> endpoint
                                .consentPage(McpAuthorizationServerEndpoints.CONSENT)
                                .authorizationRequestConverter(
                                        new McpDefaultScopeConverter(McpOAuth2Attributes.SCOPE_READ))
                                .authenticationProviders(providers -> useBoxPilotValidator(providers, userGate)))
                        .clientAuthentication(client -> client
                                .authenticationConverter(new McpPublicClientAuthenticationConverter(
                                        McpAuthorizationServerEndpoints.REVOKE))
                                .authenticationProvider(
                                        new McpPublicClientAuthenticationProvider(registeredClientRepository))))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, McpAuthorizationServerEndpoints.METADATA).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // No saved request: the entry point remembers an interrupted authorization in a cookie, so an
                // anonymous request must not be able to create a session here.
                .requestCache(RequestCacheConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        entryPoint(authorizeMatcher, pendingCookies, frontendUrl)))
                .addFilterAfter(new McpConsentCsrfFilter(consentSubmission, csrfRequestHandler), CsrfFilter.class)
                .addFilterAfter(new McpAuthenticationNormalizationFilter(authorizeMatcher, userResolver, userGate),
                        CsrfFilter.class);

        return http.build();
    }

    /**
     * Both filters are anchored behind {@code CsrfFilter}: the consent filter needs the deferred CSRF token that
     * {@code CsrfFilter} puts on the request, and both have to run long before the authorization endpoint filter,
     * which Spring Authorization Server places just ahead of the pre-authentication filters.
     */
    private static RequestMatcher matcher(String path) {
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(builder.matcher(HttpMethod.GET, path), builder.matcher(HttpMethod.POST, path));
    }

    /**
     * Only the authorize endpoint starts a login. Token and revocation are machine-to-machine calls, so an
     * unauthenticated one gets a bare {@code 401} rather than a redirect into the SPA.
     */
    private static AuthenticationEntryPoint entryPoint(RequestMatcher authorizeMatcher, McpPendingCookies cookies,
                                                       String frontendUrl) {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(authorizeMatcher,
                new McpPendingAuthorizationEntryPoint(cookies, McpUrls.withoutTrailingSlash(frontendUrl)));
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return delegating;
    }

    /**
     * Installs BoxPilot's authorize validator in place of the default one.
     *
     * <p>Spring Authorization Server offers no way to append to the validator it has already composed, so the
     * chain is rebuilt here: the strict redirect validator replaces the default one, which would accept any port
     * on a loopback redirect URI; the scope validator is the stock one; and the client-binding validator adds the
     * per-user check and re-imposes the {@code openid} restriction that comes with the default validator.
     */
    private static void useBoxPilotValidator(List<AuthenticationProvider> providers, McpUserGate userGate) {
        for (AuthenticationProvider provider : providers) {
            if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequestProvider) {
                codeRequestProvider.setAuthenticationValidator(new McpRedirectUriValidator()
                        .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                        .andThen(new McpClientBindingValidator(userGate)));
            }
        }
    }

    private static void restrictMetadataToWhatIsServed(java.util.Map<String, Object> claims) {
        UNSUPPORTED_METADATA.forEach(claims::remove);
        claims.put(OAuth2AuthorizationServerMetadataClaimNames.GRANT_TYPES_SUPPORTED, SUPPORTED_GRANT_TYPES);
        claims.put(OAuth2AuthorizationServerMetadataClaimNames.TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED,
                SUPPORTED_CLIENT_AUTHENTICATION);
        claims.put(OAuth2AuthorizationServerMetadataClaimNames.REVOCATION_ENDPOINT_AUTH_METHODS_SUPPORTED,
                SUPPORTED_CLIENT_AUTHENTICATION);
        claims.put(OAuth2AuthorizationServerMetadataClaimNames.SCOPES_SUPPORTED, SUPPORTED_SCOPES);
    }
}
