package com.sfi.conflux.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SignUtil 回归：HMAC-SHA256/SHA-256 摘要向量与常量时间 hex 比较。
 */
class SignUtilTest {

    @Test
    void hmacSha256HexKnownVector() {
        // RFC 4231 测试向量 #2：key="Jefe", data="what do ya want for nothing?"
        assertThat(SignUtil.hmacSha256Hex("what do ya want for nothing?", "Jefe"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void sha256HexKnownVector() {
        assertThat(SignUtil.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void constantTimeEqualsMatchesIgnoringCase() {
        String digest = SignUtil.hmacSha256Hex("data", "secret");
        assertThat(SignUtil.constantTimeEquals(digest, digest.toUpperCase())).isTrue();
        assertThat(SignUtil.constantTimeEquals(digest, digest)).isTrue();
        assertThat(SignUtil.constantTimeEquals(digest, digest.substring(0, 20))).isFalse();
        assertThat(SignUtil.constantTimeEquals(digest, "deadbeef")).isFalse();
        assertThat(SignUtil.constantTimeEquals(digest, null)).isFalse();
        assertThat(SignUtil.constantTimeEquals(null, "x")).isFalse();
    }
}
