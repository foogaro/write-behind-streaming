package com.foogaro.redis.wbs.core.service;

/**
 * Enum representing different caching patterns that can be used in the application.
 * Each pattern has an associated integer value that can be used for bitwise operations
 * when combining multiple patterns.
 *
 * @author Luigi Fugaro
 * @version 1.0
 * @since 1.0
 */
public enum CachingPattern {

    /**
     * No caching pattern is applied. Meaning no cache is used.
     * Value: 0
     */
    NONE(0),

    /**
     * Cache-Aside pattern (also known as Lazy Loading).
     * The application code is responsible for loading data into the cache.
     * Value: 1
     */
    CACHE_ASIDE(1),

    /**
     * Refresh-Ahead pattern.
     * The cache is proactively refreshed _before_ the data expires.
     * Value: 2
     */
    REFRESH_AHEAD(2),

    /**
     * Write-Behind pattern (also known as Write-Back).
     * Writes are made to the cache first, then asynchronously written to the database.
     * Value: 4
     */
    WRITE_BEHIND(4);

    /**
     * The integer value associated with this caching pattern.
     * Used for bitwise operations when combining multiple patterns.
     */
    private final int value;

    /**
     * Constructs a new CachingPattern with the specified value.
     *
     * @param value the integer value representing this caching pattern
     */
    CachingPattern(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value associated with this caching pattern.
     *
     * @return the integer value of this caching pattern
     */
    public int getValue() {
        return value;
    }
}
