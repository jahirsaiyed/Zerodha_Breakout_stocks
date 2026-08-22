package com.trading.zerodha;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates RFC 6238 TOTP codes.
 *
 * <p>The secret stored in the database is the raw TOTP secret provided by Zerodha
 * (shown as a QR code / text string during 2FA setup). Zerodha uses the standard
 * Base32-encoded TOTP secret; this utility decodes it and computes a 6-digit code.
 */
public final class TotpUtil {

    private static final int DIGITS = 6;
    private static final int PERIOD = 30;
    private static final String ALGORITHM = "HmacSHA1";

    private TotpUtil() {}

    /**
     * Generate the current TOTP code for a Base32-encoded secret.
     *
     * @param base32Secret the raw TOTP secret string (Base32 encoded, uppercase, may contain spaces)
     * @return 6-digit TOTP code as zero-padded string (e.g. "042719")
     */
    public static String generate(String base32Secret) {
        byte[] keyBytes = decodeBase32(base32Secret.replace(" ", "").toUpperCase());
        long counter   = Instant.now().getEpochSecond() / PERIOD;

        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hmac = hmacSha1(keyBytes, counterBytes);

        int offset = hmac[hmac.length - 1] & 0x0F;
        int code = ((hmac[offset]     & 0x7F) << 24)
                 | ((hmac[offset + 1] & 0xFF) << 16)
                 | ((hmac[offset + 2] & 0xFF) <<  8)
                 |  (hmac[offset + 3] & 0xFF);

        int otp = code % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    // ── Base32 decoder (RFC 4648, no padding required) ──────────────────────

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static byte[] decodeBase32(String s) {
        int bitCount = s.length() * 5;
        byte[] output = new byte[bitCount / 8];
        int buffer = 0, bitsLeft = 0, index = 0;

        for (char c : s.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output[index++] = (byte) (buffer >> bitsLeft);
            }
        }
        if (index < output.length) {
            byte[] trimmed = new byte[index];
            System.arraycopy(output, 0, trimmed, 0, index);
            return trimmed;
        }
        return output;
    }

    // ── HMAC-SHA1 ────────────────────────────────────────────────────────────

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("TOTP HMAC-SHA1 failed", e);
        }
    }
}
