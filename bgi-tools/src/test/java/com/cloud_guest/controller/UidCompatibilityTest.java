package com.cloud_guest.controller;

import com.cloud_guest.entitys.ClassConvert;
import com.cloud_guest.entitys.domain.UidInfo;
import com.cloud_guest.entitys.pojo.UidInfoConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UidCompatibilityTest {
    @Test
    void registeredConversionPreservesForkAccountFieldsInBothDirections() {
        new UidController();
        UidInfo original = new UidInfo("123456789", "测试账号", "游戏昵称", "奇域昵称",
                "MannequinBoy", "test-user", null, Boolean.TRUE);

        UidInfoConfig config = ClassConvert.convert(UidInfo.class, UidInfoConfig.class, original);
        UidInfo roundTrip = ClassConvert.convert(UidInfoConfig.class, UidInfo.class, config);

        assertThat(roundTrip).usingRecursiveComparison().isEqualTo(original);
    }
}
