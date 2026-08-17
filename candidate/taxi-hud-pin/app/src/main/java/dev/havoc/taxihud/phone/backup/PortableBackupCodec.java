package dev.havoc.taxihud.phone.backup;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class PortableBackupCodec {
    private static final String FORMAT = "rokid-plugin-backup";
    private static final int VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int MAX_ENCODED_CHARS = 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();

    private PortableBackupCodec() {
    }

    public static String encrypt(String appId, String plaintext, char[] password) {
        require(password != null && password.length >= 8,
                "Backup password must contain at least 8 characters");
        try {
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(salt);
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt, ITERATIONS),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(appId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return GSON.toJson(new Container(
                    FORMAT,
                    VERSION,
                    appId,
                    "PBKDF2WithHmacSHA256",
                    ITERATIONS,
                    "AES-256-GCM",
                    encode(salt),
                    encode(iv),
                    encode(ciphertext)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt backup", exception);
        }
    }

    public static String decrypt(String appId, String encoded, char[] password) {
        require(encoded != null && encoded.length() <= MAX_ENCODED_CHARS,
                "Backup file is too large");
        try {
            Container container = GSON.fromJson(encoded, Container.class);
            require(container != null, "Backup is empty");
            require(FORMAT.equals(container.format), "Unsupported backup format");
            require(container.version == VERSION, "Unsupported backup version");
            require(appId.equals(container.appId), "Backup belongs to another app");
            require("PBKDF2WithHmacSHA256".equals(container.kdf),
                    "Unsupported backup key derivation");
            require("AES-256-GCM".equals(container.cipher), "Unsupported backup cipher");
            require(container.iterations >= 100_000 && container.iterations <= 1_000_000,
                    "Unsafe backup iteration count");
            byte[] salt = decode(container.salt);
            byte[] iv = decode(container.iv);
            byte[] ciphertext = decode(container.ciphertext);
            require(salt.length == SALT_BYTES && iv.length == IV_BYTES
                            && ciphertext.length >= 16,
                    "Backup encryption parameters are invalid");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    derive(password, salt, container.iterations),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(appId));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new IllegalArgumentException("Wrong password or damaged backup", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Backup is malformed", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Backup is malformed", exception);
        }
    }

    private static SecretKeySpec derive(char[] password, byte[] salt, int iterations)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            try {
                return new SecretKeySpec(bytes, "AES");
            } finally {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] aad(String appId) {
        return (FORMAT + "|" + VERSION + "|" + appId).getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        require(value != null, "Backup encryption parameters are missing");
        return Base64.getDecoder().decode(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final class Container {
        final String format;
        final int version;
        final String appId;
        final String kdf;
        final int iterations;
        final String cipher;
        final String salt;
        final String iv;
        final String ciphertext;

        Container(String format, int version, String appId, String kdf, int iterations,
                String cipher, String salt, String iv, String ciphertext) {
            this.format = format;
            this.version = version;
            this.appId = appId;
            this.kdf = kdf;
            this.iterations = iterations;
            this.cipher = cipher;
            this.salt = salt;
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }
}
