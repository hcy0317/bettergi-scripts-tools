package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptimizationJobsTest {
    @Test void interruptedJobsRemainTerminalInHistoryAndAfterAnotherRestart() throws Exception {
        var mapper=new ObjectMapper();var store=mock(ArtifactJsonStore.class);var data=new HashMap<String,ObjectNode>();
        when(store.get(anyString(),anyString(),eq(ObjectNode.class))).thenAnswer(i->Optional.ofNullable(data.get(i.getArgument(0)+"|"+i.getArgument(1))).map(ObjectNode::deepCopy));
        when(store.put(anyString(),anyString(),any(ObjectNode.class))).thenAnswer(i->{ObjectNode value=i.getArgument(2);data.put(i.getArgument(0)+"|"+i.getArgument(1),value.deepCopy());return value;});
        when(store.listByKeyPrefixLimited(anyString(),anyString(),eq(ObjectNode.class),eq(30))).thenAnswer(i->data.entrySet().stream().filter(e->e.getKey().startsWith(i.getArgument(0)+"|"+i.getArgument(1))).map(e->e.getValue().deepCopy()).toList());
        var running=mapper.createObjectNode().put("id","job-1").put("uid","100000001").put("state","RUNNING");
        store.put("artifact-optimizer-job","100000001:job-1",running);
        store.put("artifact-optimizer-job-summary","100000001:job-1",running);
        var jobs=new OptimizationJobs(store,null,null,null,null,mapper);
        try {
            var stopped=jobs.get("100000001","job-1");
            assertEquals("INTERRUPTED",stopped.path("state").asText());
            assertFalse(stopped.path("finishedAt").asText().isBlank());
            assertEquals("INTERRUPTED",jobs.list("100000001").getFirst().path("state").asText());
            var restarted=new OptimizationJobs(store,null,null,null,null,mapper);
            try { assertEquals(stopped,restarted.get("100000001","job-1")); } finally { restarted.close(); }
        } finally { jobs.close(); }
    }
}
