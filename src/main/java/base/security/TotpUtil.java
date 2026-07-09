package base.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP implementation — no external library needed.
 * Compatible with Google Authenticator, Authy, etc.
 */
public final class TotpUtil {

    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD = 30; // seconds
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20; // 160 bits

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private TotpUtil() {}

    /**
     * Generate a random TOTP secret (base32 encoded, no padding).
     */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Build an otpauth:// URI for the user's authenticator app.
     */
    public static String buildProvisioningUri(String secret, String email, String issuer) {
        return "otpauth://totp/"
                + encodeUriComponent(issuer) + ":" + encodeUriComponent(email)
                + "?secret=" + secret
                + "&issuer=" + encodeUriComponent(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + TOTP_DIGITS
                + "&period=" + TOTP_PERIOD;
    }

    /**
     * Verify a 6-digit TOTP code against the secret.
     * Accepts current time window ±1 window (3 windows total) for clock skew tolerance.
     */
    public static boolean verify(String code, String secret) {
        if (code == null || secret == null) return false;
        if (code.length() != TOTP_DIGITS) return false;

        long currentTime = Instant.now().getEpochSecond();
        long counter = currentTime / TOTP_PERIOD;

        // Check current, previous, and next time windows
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateTOTP(secret, counter + offset);
            if (expected != null && expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    // ── internal ──

    private static String generateTOTP(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            if (key == null) return null;

            byte[] counterBytes = new byte[8];
            long c = counter;
            for (int i = 7; i >= 0; i--) {
                counterBytes[i] = (byte) (c & 0xff);
                c >>= 8;
            }

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);

            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int curByte, nextByte;

        while (i < bytes.length) {
            curByte = bytes[i++] & 0xff;
            sb.append(BASE32[(curByte >>> 3) & 0x1f]);

            if (i == bytes.length) {
                sb.append(BASE32[(curByte & 0x07) << 2]);
                break;
            }

            nextByte = bytes[i++] & 0xff;
            sb.append(BASE32[((curByte & 0x07) << 2) | ((nextByte >>> 6) & 0x03)]);

            sb.append(BASE32[(nextByte >>> 1) & 0x1f]);

            if (i == bytes.length) {
                sb.append(BASE32[(nextByte & 0x01) << 4]);
                break;
            }

            curByte = bytes[i++] & 0xff;
            sb.append(BASE32[((nextByte & 0x01) << 4) | ((curByte >>> 4) & 0x0f)]);

            if (i == bytes.length) {
                sb.append(BASE32[(curByte & 0x0f) << 1]);
                break;
            }

            nextByte = bytes[i++] & 0xff;
            sb.append(BASE32[((curByte & 0x0f) << 1) | ((nextByte >>> 7) & 0x01)]);

            sb.append(BASE32[(nextByte >>> 2) & 0x1f]);

            if (i == bytes.length) {
                sb.append(BASE32[(nextByte & 0x03) << 3]);
                break;
            }

            curByte = bytes[i++] & 0xff;
            sb.append(BASE32[((nextByte & 0x03) << 3) | ((curByte >>> 5) & 0x07)]);

            sb.append(BASE32[curByte & 0x1f]);
        }

        return sb.toString();
    }

    private static byte[] base32Decode(String encoded) {
        if (encoded == null) return null;
        String upper = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        if (upper.isEmpty()) return null;

        int byteCount = upper.length() * 5 / 8;
        byte[] result = new byte[byteCount];
        int buffer = 0, bitsLeft = 0, out = 0;

        for (char c : upper.toCharArray()) {
            int value;
            if (c >= 'A' && c <= 'Z') value = c - 'A';
            else if (c >= '2' && c <= '7') value = c - '2' + 26;
            else continue;

            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                result[out++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return result;
    }

    private static String encodeUriComponent(String s) {
        // Minimal percent-encoding for URI components
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ' ') sb.append("%20");
            else if (c == ':') sb.append("%3A");
            else if (c == '/') sb.append("%2F");
            else if (c == '?') sb.append("%3F");
            else if (c == '&') sb.append("%26");
            else if (c == '=') sb.append("%3D");
            else if (c == '@') sb.append("%40");
            else sb.append(c);
        }
        return sb.toString();
    }
}
