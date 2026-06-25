package com.dreamworks.bqom.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for storing sensitive credentials.
 *
 * <p><b>Why AES-GCM over AES-CBC?</b><br>
 * GCM is an authenticated encryption mode — it provides both confidentiality
 * AND integrity/authenticity of the ciphertext. Tampered ciphertext will cause
 * decryption to throw rather than silently produce garbage plaintext (which CBC
 * would do). This is critical when storing third-party API credentials.
 *
 * <p><b>Wire format (Base64-encoded):</b><br>
 * {@code [12-byte IV][ciphertext+16-byte GCM auth tag]}
 * The IV is randomly generated per encryption call so two encryptions of the
 * same plaintext produce different ciphertexts (semantic security).
 *
 * <p><b>Key source:</b> {@code BQOM_ENCRYPTION_KEY} environment variable —
 * a Base64-encoded 32-byte (256-bit) key. Never hardcode or commit this value.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM        = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM    = "AES";
    private static final int    GCM_IV_LENGTH    = 12;   // 96-bit IV — NIST recommended
    private static final int    GCM_TAG_BITS     = 128;  // Maximum GCM auth-tag length

    @Value("${encryption.key}")
    private String base64EncodedKey;

    private SecretKey secretKey;

    /**
     * Validates and materialises the secret key at startup so a misconfigured
     * key fails fast rather than at first encryption call.
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getUrlDecoder().decode(base64EncodedKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "BQOM_ENCRYPTION_KEY must be a Base64-encoded 32-byte (256-bit) key. " +
                            "Got " + keyBytes.length + " bytes."
            );
        }
        secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        log.info("EncryptionService initialised with AES-256-GCM");
    }

    /**
     * Encrypts {@code plaintext} and returns a Base64-encoded string that
     * embeds the random IV so it can be recovered during decryption.
     *
     * @param plaintext the credential value to encrypt (must not be null/blank)
     * @return Base64-encoded {@code [IV || ciphertext+tag]}
     * @throws EncryptionException if encryption fails for any reason
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Cannot encrypt null or blank value");
        }
        try {
            byte[] iv = generateIv();
            Cipher cipher = buildCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Prepend IV to ciphertext so we can recover it during decryption
            byte[] ivAndCiphertext = concat(iv, ciphertext);
            return Base64.getUrlEncoder().encodeToString(ivAndCiphertext);

        } catch (Exception e) {
            // Do NOT log the plaintext — it's a credential
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     *
     * @param encryptedBase64 the Base64-encoded {@code [IV || ciphertext+tag]}
     * @return the original plaintext
     * @throws EncryptionException if decryption or authentication fails
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            throw new IllegalArgumentException("Cannot decrypt null or blank value");
        }
        try {
            byte[] ivAndCiphertext = Base64.getUrlDecoder().decode(encryptedBase64);

            byte[] iv         = slice(ivAndCiphertext, 0, GCM_IV_LENGTH);
            byte[] ciphertext = slice(ivAndCiphertext, GCM_IV_LENGTH, ivAndCiphertext.length);

            Cipher cipher = buildCipher(Cipher.DECRYPT_MODE, iv);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);

        } catch (Exception e) {
            log.error("Decryption failed — possible key mismatch or data corruption", e);
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private Cipher buildCipher(int mode, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(mode, secretKey, parameterSpec);
        return cipher;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] slice(byte[] src, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(src, from, result, 0, result.length);
        return result;
    }

    // ── nested exception ───────────────────────────────────────────────────────

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}