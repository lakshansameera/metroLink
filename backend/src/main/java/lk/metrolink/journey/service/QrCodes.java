package lk.metrolink.journey.service;

import java.security.SecureRandom;

/**
 * Makes the QR code text of a new ticket (rule T8): {@code MLK-} and eight characters drawn at random.
 *
 * <p>The letters and digits that are easy to mix up (0, O, 1, I) are left out, so a code read aloud or typed in is
 * not misunderstood. There are 32 to the power of 8, about a million million, possible codes, so a code cannot be
 * guessed from a neighbour's.
 */
public final class QrCodes {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private QrCodes() {
    }

    public static String next() {
        StringBuilder code = new StringBuilder("MLK-");
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
