package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationLocalizationTest {
    @Test void translatesExactCharacterTokensButLeavesCommentsAndQuotedValuesAlone()throws Exception {
        var catalog=new ObjectMapper().readTree("""
            {"localization":{"character_names":{"amber":"安柏","aetherelectro":"空 (雷元素)"}},"characters":[{"key":"amber","inventoryAliases":["兔兔伯爵"]}]}
            """);
        assertEquals("active amber; # 安柏\n/* 兔兔伯爵 */ amber skill; print(\"安柏\"); aetherelectro attack;",
            OptimizationLocalization.translateRotation("active 安柏; # 安柏\n/* 兔兔伯爵 */ 兔兔伯爵 skill; print(\"安柏\"); 空（雷元素） attack;",catalog,Set.of("amber","aetherelectro")));
        assertThrows(IllegalArgumentException.class,()->OptimizationLocalization.translateRotation("安柏测试 attack;",catalog,Set.of("amber")));
        assertThrows(IllegalArgumentException.class,()->OptimizationLocalization.translateRotation("凯亚 attack;",catalog,Set.of("amber")));
    }
    @Test void ambiguousTravelerAliasDoesNotPreventUsingUnambiguousNames()throws Exception {
        var catalog=new ObjectMapper().readTree("""
          {"localization":{"character_names":{"aetheranemo":"空 (风元素)","aetherelectro":"空 (雷元素)"}},"characters":[{"key":"aetheranemo","inventoryAliases":["旅行者"]},{"key":"aetherelectro","inventoryAliases":["旅行者"]}]}
          """);
        var members=Set.of("aetheranemo","aetherelectro");
        assertEquals("aetherelectro skill;",OptimizationLocalization.translateRotation("空（雷元素） skill;",catalog,members));
        assertThrows(IllegalArgumentException.class,()->OptimizationLocalization.translateRotation("旅行者 skill;",catalog,members));
    }
}
