package com.velocityorm.core.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * @author sandeepkumarjakkaraju
 */
public class RedisCacheProvider implements CacheProvider {
    private final JedisPool jedisPool;

    public RedisCacheProvider(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    private byte[] getRedisKey(String cacheName, Object key) {
        String keyStr = cacheName + ":" + key.toString();
        return keyStr.getBytes();
    }

    @Override
    public void put(String cacheName, Object key, Object value) {
        if (key == null || value == null) return;
        try (Jedis jedis = jedisPool.getResource()) {
            byte[] redisKey = getRedisKey(cacheName, key);
            byte[] redisValue = SerializationUtils.serialize(value);
            jedis.set(redisKey, redisValue);
            jedis.expire(redisKey, 600); // Expire in 10 minutes
        }
    }

    @Override
    public Object get(String cacheName, Object key) {
        if (key == null) return null;
        try (Jedis jedis = jedisPool.getResource()) {
            byte[] redisKey = getRedisKey(cacheName, key);
            byte[] bytes = jedis.get(redisKey);
            return SerializationUtils.deserialize(bytes);
        } catch (Exception e) {
            return null; // Silent fail on L2 miss or error
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        if (key == null) return;
        try (Jedis jedis = jedisPool.getResource()) {
            byte[] redisKey = getRedisKey(cacheName, key);
            jedis.del(redisKey);
        }
    }

    @Override
    public void clear(String cacheName) {
        try (Jedis jedis = jedisPool.getResource()) {
            var keys = jedis.keys((cacheName + ":*").getBytes());
            if (keys != null && !keys.isEmpty()) {
                for (byte[] key : keys) {
                    jedis.del(key);
                }
            }
        }
    }
}
