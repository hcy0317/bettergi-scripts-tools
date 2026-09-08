package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.cloud_guest.artifact.job.*;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationEquipmentPlansTest {
    @TempDir Path directory;
    @Test void confirmationIsBoundSingleUseAndUnknownExecutionRequiresNewObservation()throws Exception{
        var mapper=new ObjectMapper();var data=new HashMap<String,ObjectNode>();var store=mock(ArtifactJsonStore.class);
        when(store.get(anyString(),anyString(),eq(ObjectNode.class))).thenAnswer(i->Optional.ofNullable(data.get(i.getArgument(0)+"|"+i.getArgument(1))).map(ObjectNode::deepCopy));
        when(store.put(anyString(),anyString(),any(ObjectNode.class))).thenAnswer(i->{ObjectNode value=i.getArgument(2);data.put(i.getArgument(0)+"|"+i.getArgument(1),value.deepCopy());return value;});
        var workspace=new OptimizationWorkspace(store,mapper);workspace.save("100000001",(ObjectNode)mapper.readTree("{\"version\":0,\"characters\":[{\"key\":\"amber\"}],\"builds\":[]}"));
        String revision="1234567890123456789012345678901234567890";
        var gateway=mock(GcsimGateway.class);when(gateway.catalog()).thenReturn(mapper.readTree("{\"engineRevision\":\""+revision+"\",\"characters\":[{\"key\":\"amber\",\"nativeName\":\"安柏\"}]}"));
        var items=new ArrayList<ArtifactItem>();String[] slots={"flower","plume","sands","goblet","circlet"};for(int i=0;i<5;i++)items.add(new ArtifactItem(i,"EmblemOfSeveredFate",slots[i],20,5,"atk_",List.of(),"安柏",true));
        var scans=mock(ArtifactAnalysisJobRepository.class);var snapshot=ArtifactSnapshot.create("100000001","scan","order","v1",items);
        when(scans.findById("scan")).thenReturn(Optional.of(new ArtifactAnalysisJob("scan","100000001",null,null,snapshot,null,null,"2026-09-07T00:00:00Z","2026-09-07T00:00:00Z",null)));
        var jobs=new OptimizationJobs(store,scans,workspace,new OptimizationMainStats(mapper),gateway,mapper);
        data.put("artifact-optimizer-job|100000001:job",(ObjectNode)mapper.readTree("{\"id\":\"job\",\"state\":\"COMPLETED\",\"workspaceVersion\":1,\"snapshotId\":\"scan\",\"engineRevision\":\""+revision+"\",\"result\":{\"plan\":{\"qualified\":true,\"equipment\":{\"amber\":[0,1,2,3,4]},\"reports\":{}}}}"));
        var launches=new ArtifactLaunchRequestService(directory,mapper,Clock.systemUTC(),Duration.ofMinutes(10));
        var service=new OptimizationEquipmentPlans(store,jobs,workspace,scans,launches,gateway,mapper);
        var preview=service.preview("100000001","job");String id=preview.path("id").asText(),digest=preview.path("digest").asText();
        assertThrows(IllegalStateException.class,()->service.claim("100000001",id,UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,()->service.confirm("100000001",id,"wrong-digest"));
        var confirmed=service.confirm("100000001",id,digest);String token=confirmed.path("launch").path("requestToken").asText();
        assertFalse(token.isBlank(),confirmed.toPrettyString());
        assertTrue(service.claim("100000001",id,token).path("confirmed").asBoolean());
        service.progress("100000001",id,token,(ObjectNode)mapper.readTree("{\"status\":\"needs_observation\",\"steps\":[{\"character\":\"amber\",\"artifactId\":0,\"state\":\"unknown\"}]}"));
        assertEquals("NEEDS_OBSERVATION",service.get("100000001",id).path("state").asText());
        assertThrows(IllegalStateException.class,()->service.claim("100000001",id,token));
        assertThrows(IllegalArgumentException.class,()->service.recover("100000001",id,"scan"));
        jobs.close();
    }
    @Test void inventoryOnlyDonorIsNamedInRecoveryAndCannotBypassProtection()throws Exception{
        var mapper=new ObjectMapper();var data=new HashMap<String,ObjectNode>();var store=mock(ArtifactJsonStore.class);
        when(store.get(anyString(),anyString(),eq(ObjectNode.class))).thenAnswer(i->Optional.ofNullable(data.get(i.getArgument(0)+"|"+i.getArgument(1))).map(ObjectNode::deepCopy));
        when(store.put(anyString(),anyString(),any(ObjectNode.class))).thenAnswer(i->{ObjectNode value=i.getArgument(2);data.put(i.getArgument(0)+"|"+i.getArgument(1),value.deepCopy());return value;});
        var workspace=new OptimizationWorkspace(store,mapper);workspace.save("100000001",(ObjectNode)mapper.readTree("{\"version\":0,\"characters\":[{\"key\":\"amber\"}],\"builds\":[]}"));
        String revision="1234567890123456789012345678901234567890";
        var catalog=OptimizationInventoryCatalog.attach(mapper.readTree("{\"engineRevision\":\""+revision+"\",\"characters\":[{\"id\":10000021,\"key\":\"amber\"}]}"),
            mapper.readTree("[{\"id\":10000021,\"name\":\"安柏\"},{\"id\":10000133,\"name\":\"桑多涅\",\"alias\":[\"Marionette\"]}]"));
        var gateway=mock(GcsimGateway.class);when(gateway.catalog()).thenReturn(catalog);
        var items=new ArrayList<ArtifactItem>();String[] slots={"flower","plume","sands","goblet","circlet"};
        for(int i=0;i<10;i++)items.add(new ArtifactItem(i,"EmblemOfSeveredFate",slots[i%5],20,5,"atk_",List.of(),i<5?"安柏":"Marionette",true));
        var scans=mock(ArtifactAnalysisJobRepository.class);var snapshot=ArtifactSnapshot.create("100000001","scan","order","v1",items);
        when(scans.findById("scan")).thenReturn(Optional.of(new ArtifactAnalysisJob("scan","100000001",null,null,snapshot,null,null,"2026-09-07T00:00:00Z","2026-09-07T00:00:00Z",null)));
        var jobs=new OptimizationJobs(store,scans,workspace,new OptimizationMainStats(mapper),gateway,mapper);
        try{
            var job=(ObjectNode)mapper.readTree("{\"id\":\"job\",\"state\":\"COMPLETED\",\"workspaceVersion\":1,\"snapshotId\":\"scan\",\"engineRevision\":\""+revision+"\",\"result\":{\"plan\":{\"qualified\":true,\"equipment\":{\"amber\":[5,6,7,8,9]},\"reports\":{}}}}");
            data.put("artifact-optimizer-job|100000001:job",job);
            var service=new OptimizationEquipmentPlans(store,jobs,workspace,scans,new ArtifactLaunchRequestService(directory,mapper,Clock.systemUTC(),Duration.ofMinutes(10)),gateway,mapper);
            var preview=service.preview("100000001","job");
            assertTrue(preview.path("affectedOwners").toString().contains("Marionette"));
            String after=Instant.parse(preview.path("createdAt").asText()).plusSeconds(1).toString();
            when(scans.findById("observed")).thenReturn(Optional.of(new ArtifactAnalysisJob("observed","100000001",null,null,snapshot,null,null,after,after,null)));
            var recovery=service.recover("100000001",preview.path("id").asText(),"observed");
            assertEquals(2,recovery.path("targets").size());
            assertTrue(recovery.path("targets").toString().contains("桑多涅"));
            var distinctIds=new HashSet<Integer>();for(JsonNode target:recovery.path("targets"))for(JsonNode piece:target.path("artifacts"))assertTrue(distinctIds.add(piece.asInt()));
            var changed=workspace.get("100000001");changed.putArray("protectedInventoryOwners").add("inventory10000133");workspace.save("100000001",changed);
            job.put("workspaceVersion",2);
            assertThrows(IllegalStateException.class,()->service.preview("100000001","job"));
        }finally{jobs.close();}
    }
}
