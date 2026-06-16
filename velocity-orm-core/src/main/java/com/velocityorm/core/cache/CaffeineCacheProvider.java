package com.velocityorm.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author sandeepkumarjakkaraju
 */
public class CaffeineCacheProvider implements CacheProvider {
    private final ConcurrentMap<String, Cache<Object, Object>> caches = new ConcurrentHashMap<>();

    private Cache<Object, Object> getOrCreateCache(String name) {
        return caches.computeIfAbsent(name, k -> Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build());
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        if (key == null || value == null) return;
        getOrCreateCache(cacheName).put(key, value);
    }

    @Override
    public Object get(String cacheName, Object key) {
        if (key == null) return null;
        return getOrCreateCache(cacheName).getIfPresent(key);
    }

    @Override
    public void evict(String cacheName, Object key) {
        if (key == null) return;
        getOrCreateCache(cacheName).invalidate(key);
    }

    @Override
    public void clear(String cacheName) {
        getOrCreateCache(cacheName).invalidateAll();
    }
}
