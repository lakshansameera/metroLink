package lk.metrolink.journey.domain;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * What the system keeps instead of a password (rule O6).
 *
 * <p>A password is never stored. We store a random <b>salt</b> and the result of running the password and the salt
 * through PBKDF2 many times. To check a login we run the typed password through the same steps and compare. The salt
 * means two customers with the same password get different hashes. The many repeats make guessing slow.
 *
 * <p>The hash remembers how many repeats it used, so older hashes keep working if the default is raised later.
 */
public final class PasswordHash {

    /** The number of repeats for real customers. */
    public static final int DEFAULT_ITERATIONS = 120_000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;
    private final byte[] salt;
    private final byte[] hash;

    private PasswordHash(int iterations, byte[] salt, byte[] hash) {
        this.iterations = iterations;
        this.salt = salt;
        this.hash = hash;
    }

    /** Hashes a new password with a fresh random salt. */
    public static PasswordHash of(String password) {
        return of(password, DEFAULT_ITERATIONS);
    }

    /** Same, with a chosen number of repeats. Demo data and tests use a small number so they stay fast. */
    public static PasswordHash of(String password, int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be at least 1");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new PasswordHash(iterations, salt, derive(password, salt, iterations));
    }

    /** True when {@code password} is the one this hash was made from. The comparison takes the same time either way. */
    public boolean matches(String password) {
        return MessageDigest.isEqual(derive(password, salt, iterations), hash);
    }

    /** The hash as one line of text, {@code iterations:salt:hash}, ready to keep in a database column. */
    public String encoded() {
        Base64.Encoder base64 = Base64.getEncoder();
        return iterations + ":" + base64.encodeToString(salt) + ":" + base64.encodeToString(hash);
    }

    /** Reads what {@link #encoded()} wrote. */
    public static PasswordHash parse(String encoded) {
        String[] parts = encoded.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not a password hash");
        }
        Base64.Decoder base64 = Base64.getDecoder();
        return new PasswordHash(Integer.parseInt(parts[0]), base64.decode(parts[1]), base64.decode(parts[2]));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this Java", e);
        } finally {
            spec.clearPassword();
        }
    }
}
