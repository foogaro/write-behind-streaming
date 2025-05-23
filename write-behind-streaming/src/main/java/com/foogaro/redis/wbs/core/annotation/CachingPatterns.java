package com.foogaro.redis.wbs.core.annotation;

import com.foogaro.redis.wbs.core.service.CachingPattern;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CachingPatterns {
    /**
     * Specifies the caching patterns to be used for the annotated entity.
     * Multiple patterns can be combined using bitwise OR (|).
     * For example: @CachingPatterns(patterns = {CachingPattern.CACHE_ASIDE, CachingPattern.WRITE_BEHIND})
     *
     * @return array of caching patterns
     */
    CachingPattern[] patterns() default {CachingPattern.NONE};

    /**
     * Specifies whether the caching patterns should be enabled by default.
     * If false, the patterns will need to be explicitly enabled at runtime.
     *
     * @return true if patterns are enabled by default, false otherwise
     */
    boolean enabled() default true;

    /**
     * Specifies the TTL (Time To Live) in seconds for cached entries.
     * A value of 0 or negative means no expiration.
     *
     * @return TTL in seconds
     */
    long ttl() default 0;
}
