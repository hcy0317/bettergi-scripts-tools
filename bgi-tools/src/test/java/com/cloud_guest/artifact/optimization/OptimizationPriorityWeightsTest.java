package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationPriorityWeightsTest {
    @Test void overviewWeightsReachThreeExistingEngineFieldsWithoutDuplicatingTeam()throws Exception{
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
        {"characters":[
          {"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":3,"builds":[{"id":"team","weight":2}]},
          {"key":"kaeya","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"dullblade","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":1,"builds":[{"id":"team","weight":1}]}],
         "builds":[{"id":"team","weight":5,"roundCount":3,"members":[{"character":"amber"},{"character":"kaeya"}],"rotation":"active amber;while 1 {amber attack;kaeya attack;}"}]}
        """);
        var before=workspace.deepCopy();
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var result=new OptimizationCompiler(mapper,(a,b,c)->4780).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\",\"kaeya\"],\"mode\":\"balanced\"}"));
        assertEquals(1,result.path("scenarios").size());assertEquals(5,result.path("scenarios").get(0).path("weight").asDouble());
        assertEquals(3,result.path("characters").get(0).path("weight").asDouble());
        assertEquals(2,result.path("characters").get(0).path("targets").get(0).path("weight").asDouble());
        assertEquals("balanced",result.path("mode").asText());assertEquals(before,workspace);
    }
}
