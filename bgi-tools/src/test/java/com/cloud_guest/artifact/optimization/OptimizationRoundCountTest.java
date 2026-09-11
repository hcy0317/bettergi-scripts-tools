package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationRoundCountTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void invalidCountsAndWarmupFailBeforeCompilation()throws Exception{
        for(String value:List.of("0","65","-1","1.5","null","\"3\"")){
            var build=mapper.readTree("{\"id\":\"bad\",\"roundCount\":"+value+"}");
            assertThrows(OptimizationValidationException.class,()->OptimizationSceneSettings.roundCount(build));
        }
        var invalidWarmup=mapper.readTree("{\"id\":\"bad\",\"roundCount\":3,\"roundPolicy\":{\"warmup\":3}}");
        assertThrows(OptimizationValidationException.class,()->OptimizationSceneSettings.roundCount(invalidWarmup));
        assertEquals(3,OptimizationSceneSettings.roundCount(mapper.readTree("{\"id\":\"old\"}")));
    }
    @Test void missingEngineCapabilityNeverFallsBackToLegacyStopMode()throws Exception{
        assertThrows(IllegalArgumentException.class,()->OptimizationSceneSettings.requireRoundCountEngine(mapper.createObjectNode()));
        OptimizationSceneSettings.requireRoundCountEngine(mapper.readTree("{\"capabilities\":{\"roundCountTermination\":true}}"));
    }
    @Test void bothCompilersUseSameRoundCountAndIgnoreKillDeadline()throws Exception{
        var workspace=(ObjectNode)mapper.readTree("""
        {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
        "builds":[{"id":"team","roundCount":5,"stopMode":"target_or_script","targets":[{"level":100,"resistance":0.1,"hp":999999999}],"members":[{"character":"amber"}],"rotation":"active amber;while 1 {amber attack;}","roundPolicy":{"warmup":1}}]}
        """);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var compiler=new OptimizationCompiler(mapper,(a,b,c)->4780);
        var selected=mapper.readTree("{\"characters\":[\"amber\"]}");
        for(var request:List.of(compiler.compile(workspace,snapshot,selected),compiler.compileBuild(workspace,snapshot,selected,"team"))){
            var evaluation=request.path("scenarios").get(0).path("evaluation");
            assertEquals(5,evaluation.path("roundCount").asInt());
            assertTrue(evaluation.path("autoRounds").asBoolean());
            assertFalse(evaluation.path("config").asText().contains("hp="));
        }
        assertEquals(999999999,workspace.path("builds").get(0).path("targets").get(0).path("hp").asInt());
    }
}
