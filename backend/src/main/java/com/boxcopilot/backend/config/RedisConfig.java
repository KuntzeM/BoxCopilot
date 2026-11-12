package com.boxcopilot.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
@Profile("prod")
@EnableRedisHttpSession
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

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

    /**
     * Same JDK serialization Spring Session uses by default, except that bytes that cannot be read (e.g. a session
     * written by the Boot 3.5 build, whose Spring Security 6 classes differ from Security 7) read as null. Spring Session
     * then drops that attribute, the request is anonymous and the user signs in again, instead of answering 500.
     */
    @Bean("springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return tolerantSerializer(getClass().getClassLoader());
    }

    static RedisSerializer<Object> tolerantSerializer(ClassLoader classLoader) {
        JdkSerializationRedisSerializer jdk = new JdkSerializationRedisSerializer(classLoader);
        AtomicBoolean logged = new AtomicBoolean();
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                return jdk.serialize(value);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                try {
                    return jdk.deserialize(bytes);
                } catch (SerializationException e) {
                    if (logged.compareAndSet(false, true)) {
                        log.warn("Ignoring a stored session value that cannot be deserialized ({}); "
                                + "affected users must sign in again. Further occurrences are not logged.",
                                e.getClass().getSimpleName());
                    }
                    return null;
                }
            }
        };
    }
}
