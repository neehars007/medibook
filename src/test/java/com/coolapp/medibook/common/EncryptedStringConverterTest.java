package com.coolapp.medibook.common;

import com.coolapp.medibook.common.crypto.CryptoKeyHolder;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    @BeforeAll
    static void key() {
        CryptoKeyHolder.setKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Test
    void roundTripsAndUsesRandomIv() {
        var c = new EncryptedStringConverter();
        String a = c.convertToDatabaseColumn("Acute bronchitis");
        String b = c.convertToDatabaseColumn("Acute bronchitis");
        assertThat(a).isNotEqualTo(b).doesNotContain("bronchitis");
        assertThat(c.convertToEntityAttribute(a)).isEqualTo("Acute bronchitis");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
    }
}
