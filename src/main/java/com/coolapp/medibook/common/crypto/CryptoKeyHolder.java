package com.coolapp.medibook.common.crypto;

import com.coolapp.medibook.common.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Hands the AES key (from the MEDIBOOK_ENCRYPTION_KEY env var) to the JPA converter,
 * which Hibernate instantiates outside of Spring.
 */
@Component
public class CryptoKeyHolder {

    private static volatile SecretKey key;

    private final AppProperties props;

    public CryptoKeyHolder(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        setKey(props.crypto().key());
    }

    public static void setKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("medibook.crypto.key (MEDIBOOK_ENCRYPTION_KEY) is not set");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("Encryption key must be 32 bytes (AES-256), got " + raw.length);
        }
        key = new SecretKeySpec(raw, "AES");
    }

    static SecretKey key() {
        if (key == null) {
            throw new IllegalStateException("Encryption key not initialised");
        }
        return key;
    }
}
