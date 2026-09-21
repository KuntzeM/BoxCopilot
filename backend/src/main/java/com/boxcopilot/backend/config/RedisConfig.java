package com.boxcopilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@Profile("prod")
@EnableRedisHttpSession
public class RedisConfig {

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    public RedisConfig(@Value("${spring.data.redis.host}") String redisHost,
                       @Value("${spring.data.redis.port}") int redisPort,
                       @Value("${spring.data.redis.password:}") String redisPassword) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
    }

    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(redisHost, redisPort);
        cfg.setPassword(redisPassword);
        return new LettuceConnectionFactory(cfg);
    }
}
