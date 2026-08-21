package base.l0.system.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7 generation. Time-ordered ids keep primary-key inserts sequential, which matters for the
 * journal tables — they are append-only and grow monotonically, and a random v4 key would fragment
 * every index page on the hot write path.
 */
public final class UuidUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidUtil() {
    }

    public static UUID v7() {
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        long timestamp = System.currentTimeMillis();
        value[0] = (byte) ((timestamp >>> 40) & 0xFF);
        value[1] = (byte) ((timestamp >>> 32) & 0xFF);
        value[2] = (byte) ((timestamp >>> 24) & 0xFF);
        value[3] = (byte) ((timestamp >>> 16) & 0xFF);
        value[4] = (byte) ((timestamp >>> 8) & 0xFF);
        value[5] = (byte) (timestamp & 0xFF);

        value[6] = (byte) (0x70 | (value[6] & 0x0F));   // version 7
        value[8] = (byte) (0x80 | (value[8] & 0x3F));   // IETF variant

        long high = 0;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            high = (high << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            low = (low << 8) | (value[i] & 0xFF);
        }
        return new UUID(high, low);
    }
}

