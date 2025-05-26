package com.foogaro.redis.wbs.core.service;

import com.foogaro.redis.wbs.core.annotation.CachingPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class AnnotationFinder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<Class<?>, Integer> entities = new HashMap<>();

    public boolean hasCacheAside(Class<?> entityClass) {
        checkMap(entityClass);
        return match(CachingPattern.CACHE_ASIDE.getValue(), entities.get(entityClass));
    }

    public boolean hasRefreshAhead(Class<?> entityClass) {
        checkMap(entityClass);
        return match(CachingPattern.REFRESH_AHEAD.getValue(), entities.get(entityClass));
    }

    public boolean hasWriteBehind(Class<?> entityClass) {
        checkMap(entityClass);
        return match(CachingPattern.WRITE_BEHIND.getValue(), entities.get(entityClass));
    }

    private void checkMap(Class<?> entityClass) {
        Integer cacheType = entities.get(entityClass);
        if (cacheType == null) {
            cacheType = CachingPattern.NONE.getValue();
            if (entityClass.isAnnotationPresent(CachingPatterns.class)) {
                CachingPatterns cachingPatterns = entityClass.getAnnotation(CachingPatterns.class);
                for (CachingPattern pattern : cachingPatterns.patterns()) {
                    cacheType = cacheType + pattern.getValue();
                }
            }
            entities.put(entityClass, cacheType);
        }
    }

    private boolean match(int cacheType, int entityCacheType) {
        return (cacheType & entityCacheType) > 0;
    }
}
