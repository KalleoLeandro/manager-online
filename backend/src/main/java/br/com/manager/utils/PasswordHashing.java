package br.com.manager.utils;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHashing {

    private PasswordHashing() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        var spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return (
                "pbkdf2-sha256$600000$" +
                Base64.getEncoder().encodeToString(salt) +
                "$" +
                Base64.getEncoder().encodeToString(key)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível proteger a senha.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean matches(String password, String encoded) {
        PBEKeySpec spec = null;
        try {
            var fields = encoded.split("\\$");
            spec = new PBEKeySpec(
                password.toCharArray(),
                Base64.getDecoder().decode(fields[2]),
                Integer.parseInt(fields[1]),
                256
            );
            var candidate = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(Base64.getDecoder().decode(fields[3]), candidate);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        } finally {
            if (spec != null) spec.clearPassword();
        }
    }
}
