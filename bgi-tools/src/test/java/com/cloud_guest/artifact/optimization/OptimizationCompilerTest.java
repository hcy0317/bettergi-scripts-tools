package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationCompilerTest {
    @Test void compilesSharedBuildOnceAndReservesUnselectedRealTeammate() throws Exception {
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
                {"characters":[
                {"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":1,"builds":[{"id":"team","weight":1}]},
                {"key":"kaeya","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"dullblade","weaponLevel":90,"weaponMaxLevel":90,"refinement":1}],
                "builds":[{"id":"team","weight":1,"duration":20,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber","kind":"optimized"},{"character":"kaeya","kind":"real_fixed"}],"rotation":"active amber; while 1 { amber attack; }"}]}
                """);
        var items=new java.util.ArrayList<ArtifactItem>();
        String[] slots={"flower","plume","sands","goblet","circlet"};
        for(int i=0;i<5;i++)items.add(new ArtifactItem(i,"EmblemOfSeveredFate",slots[i],20,5,"atk_",List.of(),"Kaeya",false));
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",items);
        // The existing scanner counts enhancement materials but intentionally
        // does not put them in the equippable artifact collection.
        snapshot=new ArtifactSnapshot(snapshot.uid(),snapshot.scanSessionId(),8,snapshot.orderingMode(),snapshot.catalogVersion(),snapshot.artifacts(),snapshot.snapshotDigest());
        var result=new OptimizationCompiler(mapper,(rarity,level,key)->46.6).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"],\"mode\":\"balanced\",\"budget\":64}"));
        assertEquals(1,result.path("scenarios").size());
        assertEquals(5,result.path("scenarios").get(0).path("fixedEquipment").path("kaeya").size());
        assertFalse(result.path("scenarios").get(0).path("evaluation").path("config").asText().contains("add stats"));
        assertEquals(snapshot.snapshotDigest(),result.path("inventory").path("snapshotDigest").asText());
    }
}
