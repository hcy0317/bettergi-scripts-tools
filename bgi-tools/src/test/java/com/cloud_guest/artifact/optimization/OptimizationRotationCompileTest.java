package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationRotationCompileTest {
    @Test void oneRotationDoesNotCompileUnrelatedBrokenBuildsOrModifySavedBindings() throws Exception {
        var m=new ObjectMapper();var workspace=m.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"chosen"},{"id":"unrelated"}]}],
             "builds":[{"id":"chosen","duration":30,"members":[{"character":"amber"}],"rotation":"active amber;while 1 {amber attack;}"},{"id":"unrelated","members":[{"character":"amber"}],"rotation":""}]}
            """);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"Amber",false)));
        var selection=m.readTree("{\"characters\":[\"amber\"]}");var compiler=new OptimizationCompiler(m,(a,b,c)->4780);
        assertThrows(OptimizationValidationException.class,()->compiler.compile(workspace,snapshot,selection));
        var compiled=compiler.compileBuild(workspace,snapshot,selection,"chosen");
        assertEquals(1,compiled.path("scenarios").size());assertEquals("chosen",compiled.path("scenarios").get(0).path("id").asText());
        assertEquals(2,workspace.path("characters").get(0).path("builds").size());
    }
}
