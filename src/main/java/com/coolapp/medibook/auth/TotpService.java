package com.coolapp.medibook.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;

/** RFC 6238 TOTP (SHA-1, 6 digits, 30 s) compatible with Google Authenticator / Authy. */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PERIOD = 30;
    private static final int DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String otpAuthUri(String secret, String account) {
        String label = URLEncoder.encode("MediBook:" + account, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=MediBook&digits=" + DIGITS + "&period=" + PERIOD;
    }

    /** Accepts the current code and one step either side for clock drift. */
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long step = clock.instant().getEpochSecond() / PERIOD;
        for (long s = step - 1; s <= step + 1; s++) {
            if (constantTimeEquals(code, generate(key, s, DIGITS))) {
                return true;
            }
        }
        return false;
    }

    static String generate(byte[] key, long counter, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toUpperCase().replace("=", "").toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
