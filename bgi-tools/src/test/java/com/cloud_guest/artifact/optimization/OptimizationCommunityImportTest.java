package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationCommunityImportTest {
    @Test void separatesSettingsAndRolesButKeepsCrystallizeHookAndFiniteLoop()throws Exception {
        String source="""
          noelle char lvl=90/90 cons=6 talent=9,9,9;
          noelle add weapon="serpentspine" refine=1 lvl=90/90;
          noelle add stats atk=311;
          let _execute_action = execute_action;
          fn execute_action(char_id number, action_id number, p map) {
            if action_id == .action.dash || (char_id == .keys.char.noelle && action_id == .action.attack) {
              pick_up_crystallize("any"); # retain this mechanic
            }
            return _execute_action(char_id, action_id, p);
          }
          options swap_delay=12 iteration=100;
          target lvl=100 resist=0.1 radius=2 pos=0,2.4 hp=999999999;
          energy every interval=480,720 amount=1;
          active noelle;
          for let i=0;i<4;i=i+1 { noelle attack; wait(60); }
          """;
        var result=new OptimizationCommunityImport(new ObjectMapper()).parse(source);
        assertTrue(result.path("importable").asBoolean(),result.toPrettyString());
        assertEquals(3,result.path("omittedDeclarations").size());
        assertTrue(result.path("scriptPrelude").asText().contains("pick_up_crystallize(\"any\")"));
        assertTrue(result.path("rotation").asText().contains("for let i=0;i<4;i=i+1"));
        assertFalse(result.path("rotation").asText().contains("char lvl="));assertFalse(result.path("rotation").asText().contains("energy every"));
        assertEquals("target_or_script",result.path("settings").path("stopMode").asText());
        assertEquals(2.4,result.path("settings").path("targets").get(0).path("y").asDouble());
        assertEquals(480,result.path("settings").path("energy").path("start").asInt());
        assertEquals(100,result.path("validationSamples").asInt());
    }
    @Test void stringsAndCommentsCannotMasqueradeAsDeclarations(){
        var result=new OptimizationCommunityImport(new ObjectMapper()).parse("# target hp=1;\nlet text=\"options duration=3; char lvl=1;\";\nactive amber; while 1 {amber attack;}");
        assertTrue(result.path("rotation").asText().contains("while 1"));assertTrue(result.path("scriptPrelude").asText().contains("options duration=3; char lvl=1;"));assertTrue(result.path("settings").isEmpty());
        assertFalse(new OptimizationCommunityImport(new ObjectMapper()).parse("options imaginary=9; active amber; amber attack;").path("importable").asBoolean());
    }
}
