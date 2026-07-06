package com.moneymap.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reversible AES-GCM encryption for the SMTP password ONLY — the deliberate, singular
 * exception to "always hash, never reversibly store" (Section 01B / Section 16), because
 * the application must actively use this credential. Key from MONEYMAP_ENCRYPTION_KEY.
 */
@Service
public class CryptoService {

    private final byte[] key;   // null when no key configured
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${MONEYMAP_ENCRYPTION_KEY:}") String rawKey) throws Exception {
        if (rawKey == null || rawKey.length() < 32) {
            this.key = null;
        } else {
            this.key = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean isConfigured() { return key != null; }

    public String encrypt(String plaintext) {
        if (key == null) throw new IllegalStateException("MONEYMAP_ENCRYPTION_KEY is not set (min 32 characters).");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] enc = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (key == null) throw new IllegalStateException("MONEYMAP_ENCRYPTION_KEY is not set.");
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — has MONEYMAP_ENCRYPTION_KEY changed?", e);
        }
    }
}
