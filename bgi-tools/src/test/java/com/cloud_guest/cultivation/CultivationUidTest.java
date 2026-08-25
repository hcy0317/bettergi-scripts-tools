package com.cloud_guest.cultivation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CultivationUidTest {
    @Test
    void acceptsAndNormalizesCanonicalNumericUid() {
        assertThat(CultivationUid.normalize(" 102550550 ")).isEqualTo("102550550");
    }

    @Test
    void rejectsPathSegmentsAndNonNumericUidValues() {
        assertThatThrownBy(() -> CultivationUid.normalize("..\\..\\outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字");
        assertThatThrownBy(() -> CultivationUid.normalize("123456/../789012"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字");
    }
}
