package com.velocityorm.core.cache;

/**
 * @author sandeepkumarjakkaraju
 */
public interface CacheProvider {
    void put(String cacheName, Object key, Object value);
    Object get(String cacheName, Object key);
    void evict(String cacheName, Object key);
    void clear(String cacheName);
}
