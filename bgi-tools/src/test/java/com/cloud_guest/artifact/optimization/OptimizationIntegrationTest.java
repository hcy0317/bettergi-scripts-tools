package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OptimizationIntegrationTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void publishedStatSupplementMatchesGoodUnitsAtAllRarities(){
        var data=new OptimizationMainStats(mapper);
        assertEquals(46.6,data.value(5,20,"atk_"),0.0001);
        assertEquals(4780,data.value(5,20,"hp"),0.1);
        assertEquals(31.1,data.value(5,20,"critRate_"),0.0001);
        assertTrue(data.value(4,16,"enerRech_")>30);
        assertThrows(IllegalArgumentException.class,()->data.value(4,20,"atk_"));
    }
    @Test void enkaUsesPinnedEngineIdsAndPreservesUnknownTalents() throws Exception {
        var catalog=mapper.readTree("""
                {"characters":[{"id":10000021,"key":"amber","skill_details":{"attack":10021,"skill":10018,"burst":10019}}],"weapons":[{"id":15101,"key":"huntersbow"}]}
                """);
        var source=mapper.readTree("""
                {"avatarInfoList":[{"avatarId":10000021,"propMap":{"4001":{"val":"80"},"1002":{"val":"6"}},"skillLevelMap":{"10021":8,"10018":9},"equipList":[{"itemId":15101,"weapon":{"level":80,"promoteLevel":6,"affixMap":{"1":2}}}]}]}
                """);
        var result=new OptimizationEnka(mapper).convert(source,catalog);var c=result.path("characters").get(0);
        assertEquals("amber",c.path("key").asText());assertEquals(80,c.path("level").asInt());assertEquals(3,c.path("refinement").asInt());assertTrue(c.path("talents").get(2).isNull());assertFalse(result.path("warnings").isEmpty());
    }
    @Test void existingSnapshotAndManualBuildReachRealBoundedEngine() throws Exception {
        String exe=System.getProperty("artifact.optimizer.test.executable","");assumeTrue(!exe.isBlank(),"provide the built bridge explicitly for integration");
        var gateway=new GcsimGateway(mapper,null,exe);
        var catalog=gateway.catalog();assertTrue(catalog.path("characters").size()>12);
        var workspace=mapper.readTree("""
                {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":1,"builds":[{"id":"team","weight":1}]}],
                "builds":[{"id":"team","weight":1,"duration":6,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber","kind":"real_fixed"}],"rotation":"active amber; while 1 { amber attack; }"}]}
                """);
        String[] slots={"flower","plume","sands","goblet","circlet"},keys={"hp","atk","atk_","pyro_dmg_","critRate_"};
        var items=new ArrayList<ArtifactItem>();for(int i=0;i<5;i++)items.add(new ArtifactItem(i,"EmblemOfSeveredFate",slots[i],20,5,keys[i],List.of(),"Amber",false));
        var snapshot=ArtifactSnapshot.create("100000001","test-scan","default","test-catalog",items);
        var request=new OptimizationCompiler(mapper,new OptimizationMainStats(mapper)).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"],\"budget\":16}"));
        var job=mapper.createObjectNode();job.set("optimization",request);job.putObject("limits").put("wallTimeMs",15000);
        var response=gateway.execute("--optimize",job,Duration.ofSeconds(20));
        assertEquals("completed",response.path("status").asText(),response.toPrettyString());
        var plan=response.path("result").path("plan");assertTrue(plan.path("qualified").asBoolean(),response.toPrettyString());assertEquals(5,plan.path("equipment").path("amber").size());assertTrue(plan.path("reports").path("team").path("meanDps").asDouble()>0);
    }
}
