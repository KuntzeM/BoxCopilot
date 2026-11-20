package com.boxcopilot.backend.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Serves the SPA build that the Docker image places under {@code classpath:/static/} (the locations follow
 * {@code spring.web.resources.static-locations}), and sets the security headers on every response.
 *
 * <ul>
 *   <li>{@code /assets/**}: Vite's content-hashed bundles, cached for a year and immutable; a missing one is 404.</li>
 *   <li>every other file ({@code index.html}, favicon, ...): {@code Cache-Control: no-cache}, because
 *       {@code index.html} names the hashed bundles and must be revalidated on every visit.</li>
 *   <li>a GET for a path without a file extension outside the server's own prefixes ({@link SpaRoutes}) that
 *       matched no file is a client-side route and is answered with {@code index.html}.</li>
 * </ul>
 *
 * <p>Ordered first so that Spring Boot sees {@code /**} taken and does not register its own default mapping.
 */
@Configuration(proxyBeanMethods = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaWebConfig implements WebMvcConfigurer {

    static final String INDEX = "index.html";

    private final List<String> locations;

    public SpaWebConfig(@Value("${spring.web.resources.static-locations:classpath:/static/}") String[] locations) {
        this.locations = Arrays.stream(locations).map(String::trim)
                .map(location -> location.endsWith("/") ? location : location + "/")
                .toList();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(locations.stream().map(location -> location + "assets/").toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(locations.toArray(String[]::new))
                .setCacheControl(CacheControl.noCache())
                // no resolution cache: whether a path falls back to index.html depends on the Accept header
                .resourceChain(false)
                .addResolver(new SpaFallbackResourceResolver());
    }

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter(
            @Value("${app.security.headers.csp-mode:enforce}") String cspMode,
            @Value("${app.security.headers.hsts-enabled:false}") boolean hstsEnabled) {
        FilterRegistrationBean<SecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new SecurityHeadersFilter(cspMode, hstsEnabled));
        // ahead of Spring Security, so that its early answers (401, 403, 429) carry the headers as well
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        return registration;
    }

    /** Resolves a real file first; a client-side route that matched none resolves to {@code index.html}. */
    static final class SpaFallbackResourceResolver extends PathResourceResolver {

        @Override
        protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
                                                   List<? extends Resource> locations, ResourceResolverChain chain) {
            boolean directory = requestPath.isEmpty() || requestPath.endsWith("/");
            if (!directory) {
                Resource resource = super.resolveResourceInternal(request, requestPath, locations, chain);
                if (resource != null) {
                    return resource;
                }
            }
            if (request == null || !SpaRoutes.isClientSideRoute(requestPath, request)) {
                return null;
            }
            return super.resolveResourceInternal(request, INDEX, locations, chain);
        }
    }
}
