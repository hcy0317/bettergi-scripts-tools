package com.cloud_guest.artifact.optimization;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationWeaponWaitTest {
    @Test void commentsStringsComplexBodiesAndUnknownWeaponsAreNotRewritten() {
        var weapons=Map.of("furina","fleuvecendreferryman");
        for(String source:java.util.List.of(
            "# while !.furina.mods.favonius-cd {furina attack;}\nfurina skill;",
            "let note=\"while !.furina.mods.favonius-cd {furina attack;}\";",
            "while !.furina.mods.favonius-cd {furina attack; furina skill;}",
            "fn custom(){ add_stat_mod(\"favonius-cd\"); } while !.furina.mods.favonius-cd {furina attack;}"))
            assertEquals(source,OptimizationScriptParts.adaptImpossibleWeaponWaits(source,weapons).config());
        String source="while !.furina.mods.favonius-cd {furina attack;}";
        assertEquals(source,OptimizationScriptParts.adaptImpossibleWeaponWaits(source,Map.of()).config());
    }
    @Test void pureImpossibleWaitIsRemovedOnlyFromTheCalculationCopy() {
        String source="for let i=0;i<4;i=i+1 {\nwhile !.furina.mods.favonius-cd { furina attack; }\nfurina skill;\n}";
        var result=OptimizationScriptParts.adaptImpossibleWeaponWaits(source,Map.of("furina","fleuvecendreferryman"));
        assertFalse(result.config().contains("while !"));
        assertTrue(result.config().contains("furina skill;"));
        assertEquals(source.chars().filter(c->c=='\n').count(),result.config().chars().filter(c->c=='\n').count());
        assertEquals(java.util.List.of("removed_impossible_favonius_wait:furina"),result.assumptions());
        assertTrue(source.contains("while !"));
        assertEquals(source,OptimizationScriptParts.adaptImpossibleWeaponWaits(source,Map.of("furina","favoniussword")).config());
    }
    @Test void personalWeaponCannotFulfilACommunityFavoniusWait() {
        String source="while !.furina.mods.favonius-cd { furina attack; }";
        assertThrows(OptimizationValidationException.class,()->OptimizationScriptParts.requireCompatibleWeaponWaits(source,"team",Map.of("furina","fleuvecendreferryman")));
        assertDoesNotThrow(()->OptimizationScriptParts.requireCompatibleWeaponWaits(source,"team",Map.of("furina","favoniussword")));
        assertDoesNotThrow(()->OptimizationScriptParts.requireCompatibleWeaponWaits("# "+source,"team",Map.of("furina","fleuvecendreferryman")));
    }
}
