package com.trading.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EncryptionUtilTest {
    private final EncryptionUtil util = new EncryptionUtil("test-key-32-characters-long!!!!!");

    @Test
    void encryptAndDecrypt_roundTrips() {
        String plaintext = "my-secret-api-key";
        String encrypted = util.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(util.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String a = util.encrypt("same");
        String b = util.encrypt("same");
        assertThat(a).isNotEqualTo(b);
    }
}
