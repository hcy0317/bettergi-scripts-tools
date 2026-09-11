package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.cloud_guest.artifact.job.*;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OptimizationOutputBudgetTest {
    @ParameterizedTest
    @CsvSource({"equipment,64,16384", "equipment,65,65536", "rotation,64,16384", "rotation,65,65536", "rotation-adapted,65,65536", "rotation-member-profile,65,65536", "equipment-adapted,65,65536", "equipmentUnsupported,65,65536", "rotationUnsupported,65,65536"})
    void entryPointsBudgetCompleteEvidenceWithoutChangingUserChoices(String kind,int samples,int outputKiB)throws Exception {
        var m=new ObjectMapper();var memory=new ConcurrentHashMap<String,ObjectNode>();
        var store=mock(ArtifactJsonStore.class);
        when(store.get(anyString(),anyString(),eq(ObjectNode.class))).thenAnswer(i->Optional.ofNullable(memory.get(i.getArgument(0)+"|"+i.getArgument(1))).map(ObjectNode::deepCopy));
        when(store.put(anyString(),anyString(),any(ObjectNode.class))).thenAnswer(i->{ObjectNode v=i.getArgument(2);memory.put(i.getArgument(0)+"|"+i.getArgument(1),v.deepCopy());return v;});
        var w=mock(OptimizationWorkspace.class);
        when(w.get("100000001")).thenReturn((ObjectNode)m.readTree("""
            {"version":1,"characters":[{"key":"amber","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"builds":[{"id":"team"}]}],
            "builds":[{"id":"team","duration":30,"members":[{"character":"amber"}],"rotation":"active amber;while 1 {amber attack;}"}]}
            """));
        var items=new ArrayList<ArtifactItem>();String[] slots={"flower","plume","sands","goblet","circlet"};
        for(int i=0;i<5;i++)items.add(new ArtifactItem(i,"EmblemOfSeveredFate",slots[i],20,5,"atk_",List.of(),"Amber",false));
        var scans=mock(ArtifactAnalysisJobRepository.class);
        var snapshot=ArtifactSnapshot.create("100000001","scan","default","v1",items);
        when(scans.findById("scan")).thenReturn(Optional.of(new ArtifactAnalysisJob("scan","100000001",null,null,snapshot,null,null,"","",null)));
        var gateway=mock(GcsimGateway.class);
        when(gateway.catalog()).thenReturn(m.readTree("{\"engineRevision\":\"1234567890123456789012345678901234567890\",\"capabilities\":{\"roundCountTermination\":true},\"characters\":[{\"key\":\"amber\"}]}"));
        if(kind.endsWith("Unsupported"))((ObjectNode)gateway.catalog().path("capabilities")).put("roundCountTermination",false);
        var received=new LinkedBlockingQueue<JsonNode>();
        when(gateway.execute(anyString(),any(),any())).thenAnswer(i->{received.add(((JsonNode)i.getArgument(1)).deepCopy());return m.readTree("{\"status\":\"completed\",\"result\":{}}");});
        var stats=new OptimizationMainStats(m);var jobs=new OptimizationJobs(store,scans,w,stats,gateway,m);
        try {
            var workspace=w.get("100000001");
            if(kind.contains("-")){
                var build=(ObjectNode)workspace.path("builds").get(0);
                build.put("rotation","active amber; while 1 {while !.amber.mods.favonius-cd {amber attack;} amber skill;}");
                build.put("scriptPrelude","let retained = 3;");
                if(kind.equals("rotation-member-profile")){
                    ((ObjectNode)build.path("members").get(0)).set("profile",workspace.path("characters").get(0).deepCopy());
                    ((ObjectNode)workspace.path("characters").get(0)).put("weapon","favoniuswarbow");
                }
            }
            var savedWorkspace=workspace.deepCopy();
            var selection=(ObjectNode)m.readTree("{\"workspaceVersion\":1,\"snapshotId\":\"scan\",\"buildId\":\"team\",\"characters\":[\"amber\"],\"wallTimeSeconds\":120,\"searchSamples\":3}");
            selection.put("validationSamples",samples);var before=selection.deepCopy();
            if(kind.endsWith("Unsupported")){
                var error=assertThrows(IllegalArgumentException.class,()->{
                    if(kind.startsWith("equipment"))jobs.start("100000001",selection);
                    else new OptimizationRotationService(w,scans,stats,gateway,jobs,null,m).start("100000001",selection);
                });
                assertTrue(error.getMessage().contains("循环次数"));
                verify(gateway,never()).execute(anyString(),any(),any());
                verify(store,never()).put(eq("artifact-optimizer-job"),anyString(),any(ObjectNode.class));
                return;
            }
            if(kind.contains("-")){
                assertThrows(OptimizationValidationException.class,()->{
                    if(kind.startsWith("equipment"))jobs.start("100000001",selection);
                    else new OptimizationRotationService(w,scans,stats,gateway,jobs,null,m).start("100000001",selection);
                });
                assertTrue(received.isEmpty());
                verify(gateway,never()).execute(anyString(),any(),any());
                verify(store,never()).put(eq("artifact-optimizer-job"),anyString(),any(ObjectNode.class));
                assertEquals(before,selection);assertEquals(savedWorkspace,workspace);
                return;
            }
            if(kind.equals("equipment"))jobs.start("100000001",selection);
            else new OptimizationRotationService(w,scans,stats,gateway,jobs,null,m).start("100000001",selection);
            var payload=received.poll(5,TimeUnit.SECONDS);assertNotNull(payload);
            assertEquals(outputKiB,payload.path("limits").path("outputKiB").asInt());
            assertEquals(120000,payload.path("limits").path("wallTimeMs").asInt());
            assertEquals(768,payload.path("limits").path("memoryMiB").asInt());
            var request=payload.path(kind.equals("equipment")?"optimization":"rotation");
            var evaluation=kind.equals("equipment")?request.path("scenarios").get(0).path("evaluation"):request.path("base");
            assertEquals(3,evaluation.path("roundCount").asInt());assertTrue(evaluation.path("autoRounds").asBoolean());
            assertEquals(samples,request.path("validationSeeds").size());assertEquals(3,request.path("searchSeeds").size());
            assertEquals(before,selection);
            assertEquals(savedWorkspace,workspace);
        } finally {jobs.close();}
    }
}
