package com.velocityorm.core.security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author sandeepkumarjakkaraju
 */
public class EncryptionService {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public EncryptionService(String secretKeyHex) {
        byte[] keyBytes = new byte[32];
        byte[] userKeyBytes = secretKeyHex.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(userKeyBytes, 0, keyBytes, 0, Math.min(userKeyBytes.length, 32));
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }
    
    public String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            
            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }
    
    public String decrypt(String base64Value) {
        if (base64Value == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(base64Value);
            
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);
            
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }
}
