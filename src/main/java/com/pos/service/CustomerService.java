package com.pos.service;

import com.pos.model.Customer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Sign-up checks and password/token hashing for the customer phone app. */
public final class CustomerService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int ITERATIONS = 120_000;

    private CustomerService() {
    }

    public static Customer validateProfile(String fullName, String phone, String email, String address) throws ValidationException {
        String cleanName = clean(fullName);
        if (cleanName.isEmpty()) {
            throw new ValidationException("fullName", "Full name is required");
        } else if (cleanName.length() > 100) {
            throw new ValidationException("fullName", "Full name can be at most 100 characters");
        }

        String cleanPhone = clean(phone).replaceAll("[\\s()-]", "");
        if (!cleanPhone.matches("\\+?\\d{7,15}")) {
            throw new ValidationException("phone", "Enter a valid mobile number, for example 09171234567");
        }

        String cleanEmail = clean(email).toLowerCase();
        if (!cleanEmail.isEmpty() && (cleanEmail.length() > 150 || !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new ValidationException("email", "Enter a valid e-mail address, or leave it empty");
        }

        String cleanAddress = clean(address);
        if (cleanAddress.length() > 255) {
            throw new ValidationException("address", "Address can be at most 255 characters");
        }

        return new Customer(0, cleanName, cleanPhone, cleanEmail.isEmpty() ? null : cleanEmail, cleanAddress);
    }

    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < 6) {
            throw new ValidationException("password", "Password must be at least 6 characters");
        } else if (password.length() > 100) {
            throw new ValidationException("password", "Password can be at most 100 characters");
        }
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HEX.formatHex(salt);
    }

    public static String hashPassword(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HEX.parseHex(salt), ITERATIONS, 256);
            return HEX.formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        }
    }

    public static boolean passwordMatches(String password, String salt, String expectedHash) {
        return password != null
            && MessageDigest.isEqual(hashPassword(password, salt).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /** A random sign-in token for the phone. Only its SHA-256 is stored. */
    public static String newToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return HEX.formatHex(token);
    }

    public static String hashToken(String token) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
