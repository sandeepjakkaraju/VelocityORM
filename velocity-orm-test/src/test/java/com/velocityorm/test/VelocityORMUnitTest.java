package com.velocityorm.test;

import com.velocityorm.core.cache.CaffeineCacheProvider;
import com.velocityorm.core.cache.L1Cache;
import com.velocityorm.core.query.LambdaExpressionParser;
import com.velocityorm.core.security.EncryptionService;
import com.velocityorm.test.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author sandeepkumarjakkaraju
 */
public class VelocityORMUnitTest {

    @Test
    public void testEncryptionService() {
        EncryptionService encryption = new EncryptionService("my_super_secret_key_123456789012");
        String original = "sandeep@example.com";
        String encrypted = encryption.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = encryption.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testL1Cache() {
        L1Cache cache = new L1Cache();
        User user = new User();
        user.setId(1L);
        user.setName("Sandeep");

        cache.put(User.class, 1L, user);
        User cached = cache.get(User.class, 1L);
        assertNotNull(cached);
        assertEquals("Sandeep", cached.getName());

        cache.remove(User.class, 1L);
        assertNull(cache.get(User.class, 1L));
    }

    @Test
    public void testCaffeineL2Cache() {
        CaffeineCacheProvider provider = new CaffeineCacheProvider();
        User user = new User();
        user.setId(1L);
        user.setName("Sandeep");

        provider.put("users", 1L, user);
        User cached = (User) provider.get("users", 1L);
        assertNotNull(cached);
        assertEquals("Sandeep", cached.getName());

        provider.evict("users", 1L);
        assertNull(provider.get("users", 1L));
    }

    @Test
    public void testLambdaExpressionParser() {
        String propId = LambdaExpressionParser.getPropertyName(User::getId);
        String propName = LambdaExpressionParser.getPropertyName(User::getName);
        String propEmail = LambdaExpressionParser.getPropertyName(User::getEmail);

        assertEquals("id", propId);
        assertEquals("name", propName);
        assertEquals("email", propEmail);
    }
}
