package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationScriptDiagnosticsTest {
    @Test void sharedOriginalSourceFixturesKeepUtf16Coordinates()throws Exception{
        var m=new ObjectMapper();
        try(var stream=getClass().getResourceAsStream("/artifact-optimizer/script-diagnostics.json")){
            for(var fixture:m.readTree(stream)){
                Map<String,String> weapons=m.convertValue(fixture.path("weapons"),new com.fasterxml.jackson.core.type.TypeReference<>(){});
                Map<String,String> aliases=m.convertValue(fixture.path("aliases"),new com.fasterxml.jackson.core.type.TypeReference<>(){});
                var issues=OptimizationScriptDiagnostics.diagnose(fixture.path("source").asText(),"team","rotation",weapons,aliases);
                assertEquals(fixture.path("expected").size(),issues.size(),fixture.path("name").asText());
                for(int i=0;i<issues.size();i++){
                    JsonNode actual=m.valueToTree(issues.get(i)),expected=fixture.path("expected").get(i);
                    for(String field:List.of("startOffset","endOffset","line","column","character"))assertEquals(expected.path(field),actual.path(field),fixture.path("name").asText()+":"+field);
                }
            }
        }
    }
    @Test void originalFieldsCarryCoordinatesBeforeAnyCompilation()throws Exception{
        var m=new ObjectMapper();var workspace=m.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
            "builds":[{"id":"team","duration":30,"members":[{"character":"amber"}],"rotation":"active kaeya;","scriptPrelude":"while !.amber.mods.favonius-cd {amber attack;}"}]}
            """);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var issues=OptimizationInputValidation.validate(m,workspace,m.readTree("{\"characters\":[\"amber\"]}"),null,snapshot);
        var json=m.valueToTree(issues);
        assertEquals(2,json.size());
        assertTrue(json.toString().contains("startOffset"));
        assertEquals(Set.of("rotation","scriptPrelude"),new HashSet<>(json.findValuesAsText("field")));
    }
}
