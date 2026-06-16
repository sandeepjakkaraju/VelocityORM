package com.velocityorm.core.cache;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * @author sandeepkumarjakkaraju
 */
public class HazelcastCacheProvider implements CacheProvider {
    private final HazelcastInstance hazelcastInstance;

    public HazelcastCacheProvider(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    private IMap<Object, Object> getMap(String cacheName) {
        return hazelcastInstance.getMap(cacheName);
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        if (key == null || value == null) return;
        getMap(cacheName).put(key, value);
    }

    @Override
    public Object get(String cacheName, Object key) {
        if (key == null) return null;
        try {
            return getMap(cacheName).get(key);
        } catch (Exception e) {
            return null; // Silent fail on L2 miss or error
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        if (key == null) return;
        getMap(cacheName).remove(key);
    }

    @Override
    public void clear(String cacheName) {
        getMap(cacheName).clear();
    }
}
