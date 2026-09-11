package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationCompilerTest {
    @Test void compilerRejectsContradictoryScriptWithoutRewritingIt()throws Exception{
        var m=new ObjectMapper();
        var workspace=m.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
             "builds":[{"id":"team","duration":20,"members":[{"character":"amber"}],"rotation":"active amber; while !.amber.mods.favonius-cd {amber attack;} amber skill;"}]}
            """);
        String original=workspace.path("builds").get(0).path("rotation").asText();
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var selection=m.readTree("{\"characters\":[\"amber\"]}");
        var error=assertThrows(OptimizationValidationException.class,()->new OptimizationCompiler(m,(a,b,c)->4780).compile(workspace,snapshot,selection));
        assertEquals("rotation",error.issues().get(0).field());
        assertNotNull(error.issues().get(0).startOffset());
        assertEquals(original,workspace.path("builds").get(0).path("rotation").asText());
    }

    @Test void fixedArtifactsMustBeBoundToTheSelectedPhysicalSnapshot()throws Exception{
        var mapper=new ObjectMapper();var workspace=mapper.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"fixedSlots":{"flower":0},"builds":[{"id":"team"}]}],
             "builds":[{"id":"team","duration":20,"members":[{"character":"amber"}],"rotation":"active amber; amber attack;"}]}
            """);
        var profile=(com.fasterxml.jackson.databind.node.ObjectNode)workspace.path("characters").get(0);
        var item=new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false);
        var snapshot=ArtifactSnapshot.create("100000001","first","default","v1",List.of(item));
        var selection=mapper.readTree("{\"characters\":[\"amber\"]}");
        var compiler=new OptimizationCompiler(mapper,(a,b,c)->4780);
        var missing=assertThrows(OptimizationValidationException.class,()->compiler.compile(workspace,snapshot,selection));
        assertTrue(missing.issues().stream().anyMatch(i->i.field().equals("fixedSlots")&&i.character().equals("amber")));
        profile.putObject("fixedSlotBindings").putObject("flower").put("scanIndex",0).put("uid",snapshot.uid()).put("scanSessionId",snapshot.scanSessionId()).put("snapshotDigest",snapshot.snapshotDigest()).put("fingerprint",item.contentFingerprint());
        assertEquals(0,compiler.compile(workspace,snapshot,selection).path("characters").get(0).path("fixedSlots").path("flower").asInt(-1));
        var rescan=ArtifactSnapshot.create("100000001","second","default","v1",List.of(new ArtifactItem(0,"GladiatorsFinale","flower",0,5,"hp",List.of(),"",false)));
        assertThrows(OptimizationValidationException.class,()->compiler.compile(workspace,rescan,selection));
    }
    @Test void currentlyDormantAffixIsNotAppliedOrRemovedFromPhysicalIdentity()throws Exception{
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
             "builds":[{"id":"team","duration":20,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber"}],"rotation":"active amber; amber attack;"}]}
            """);
        var item=new ArtifactItem(0,"EmblemOfSeveredFate","flower",0,5,"hp",List.of(
            new ArtifactSubstat("critRate_",3.9,false),new ArtifactSubstat("critDMG_",7.8,true)),"",false);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(item));
        var result=new OptimizationCompiler(mapper,(a,b,c)->717).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"]}"));
        assertEquals(1,result.path("items").get(0).path("substats").size());
        assertEquals("critRate_",result.path("items").get(0).path("substats").get(0).path("key").asText());
        assertEquals(item.contentFingerprint(),result.path("items").get(0).path("fingerprint").asText());
        assertEquals(2,snapshot.artifacts().get(0).substats().size());
    }

    @Test void inventoryOnlyOwnerCanBeProtectedWithoutAPersonalSimulationProfile()throws Exception{
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
             "protectedInventoryOwners":["inventory10000133"],
             "builds":[{"id":"team","duration":20,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber"}],"rotation":"active amber; amber attack;"}]}
            """);
        var catalog=OptimizationInventoryCatalog.attach(mapper.readTree("""
            {"characters":[{"key":"amber","id":10000021}]}
            """),mapper.readTree("""
            [{"id":10000021,"name":"安柏"},{"id":10000133,"name":"桑多涅","alias":["Marionette"]}]
            """));
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(
            new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"Marionette",false)));
        var result=new OptimizationCompiler(mapper,(a,b,c)->4780,catalog).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"]}"));
        assertEquals("inventory10000133",result.path("items").get(0).path("location").asText());
        assertEquals("inventory10000133",result.path("protectedCharacters").get(0).asText());
        assertEquals(1,result.path("characters").size());
        assertFalse(result.path("scenarios").get(0).path("evaluation").path("config").asText().contains("inventory10000133 char"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)workspace).putArray("protectedInventoryOwners").add("unverified");
        var invalid=assertThrows(OptimizationValidationException.class,()->new OptimizationCompiler(mapper,(a,b,c)->4780,catalog).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"]}")));
        assertTrue(invalid.issues().stream().anyMatch(i->i.field().equals("protections")));
    }

    @Test void sceneSettingsUseRoundsPreservingParticlesAndIndependentSampling()throws Exception {
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
          {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
           "builds":[{"id":"team","duration":60,"stopMode":"target_or_script","targets":[{"level":100,"resistance":0.1,"radius":2,"x":0,"y":2.4,"hp":999999999}],"swapDelay":12,"energy":{"enabled":true,"mode":"every","start":480,"end":720,"amount":1},"roundPolicy":{"mode":"auto","warmup":0},"members":[{"character":"amber"}],"scriptPrelude":"let prior = execute_action; fn execute_action(char_id number, action_id number, p map) { return prior(char_id, action_id, p); }","rotation":"active amber; for let i=0;i<4;i=i+1 {amber attack;}"}]}
          """);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var result=new OptimizationCompiler(mapper,(a,b,c)->4780).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"],\"searchSamples\":3,\"validationSamples\":100}"));
        var evaluation=result.path("scenarios").get(0).path("evaluation");String config=evaluation.path("config").asText();
        assertFalse(config.contains("hp=999999999"),config);assertTrue(config.contains("duration=600"),config);assertEquals(3,evaluation.path("roundCount").asInt());
        assertEquals(999999999,workspace.path("builds").get(0).path("targets").get(0).path("hp").asInt());
        assertTrue(config.contains("energy every interval=480,720 amount=1;"));assertTrue(config.contains("swap_delay=12"));assertTrue(config.contains("fn execute_action"));assertTrue(evaluation.path("autoRounds").asBoolean());
        assertEquals(100,result.path("validationSeeds").size());assertEquals(3,result.path("searchSeeds").size());
    }
    @Test void chineseRoleNamesCompileWithoutChangingCommentsOrStrings() throws Exception {
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("""
            {"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
             "builds":[{"id":"team","duration":20,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber"}],"rotation":"active 安柏; while 1 { 安柏 attack; } # 安柏的注释"}]}
            """);
        var catalog=mapper.readTree("""
            {"localization":{"character_names":{"amber":"安柏"}},"characters":[{"key":"amber","id":10000021,"nativeName":"安柏","inventoryAliases":["兔兔伯爵"]}]}
            """);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var result=new OptimizationCompiler(mapper,(a,b,c)->4780,catalog).compile(workspace,snapshot,mapper.readTree("{\"characters\":[\"amber\"]}"));
        String script=result.path("scenarios").get(0).path("evaluation").path("config").asText();
        assertTrue(script.contains("active amber; while 1 { amber attack; }"),script);
        assertTrue(script.contains("# 安柏的注释"));
    }
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
        ((com.fasterxml.jackson.databind.node.ArrayNode)workspace.path("characters"))
                .addObject().put("key","marionette").put("protected",true);
        items.add(new ArtifactItem(5,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"桑多涅",false));
        var nativeCatalog=mapper.readTree("""
            {"characters":[{"id":"10000021","key":"amber"},{"id":"10000015","key":"kaeya"}],
             "inventoryCharacters":[{"id":"10000133","key":"marionette","inventoryAliases":["桑多涅"]}]}
            """);
        var withDonor=new OptimizationCompiler(mapper,(rarity,level,key)->46.6,nativeCatalog).compile(
                workspace,ArtifactSnapshot.create("100000001","scan","default","v1",items),
                mapper.readTree("{\"characters\":[\"amber\"],\"budget\":64}"));
        assertEquals(6,withDonor.path("items").size());
        assertEquals("marionette",withDonor.path("items").get(5).path("location").asText());
        assertEquals("marionette",withDonor.path("protectedCharacters").get(0).asText());
        assertFalse(withDonor.path("scenarios").get(0).path("evaluation").path("config").asText().contains("marionette char"));
    }
}
