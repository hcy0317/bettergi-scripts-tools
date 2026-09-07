package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationRotationCompilerTest {
    @Test void supportedNativeActionsRoundTripAndUnknownGuardsNeverDisappear(){
        var compiler=new OptimizationRotationCompiler(new ObjectMapper());
        var parsed=compiler.parse("安柏 e,attack(2)\n凯亚 q(required)",Map.of("安柏","amber","凯亚","kaeya"));
        assertTrue(parsed.path("supported").asBoolean(),parsed.toPrettyString());assertEquals(3,parsed.path("actions").size());
        String nativeText=compiler.nativeScript(parsed.path("actions"),Map.of("amber","安柏","kaeya","凯亚"),"moderate");
        assertTrue(nativeText.contains("required"));assertTrue(nativeText.contains("安柏 attack(2.0"));
        var unsupported=compiler.parse("安柏 e(record=护盾),attack(2,keep=护盾)",Map.of("安柏","amber"));
        assertFalse(unsupported.path("supported").asBoolean());assertFalse(unsupported.path("issues").isEmpty());
    }
}
