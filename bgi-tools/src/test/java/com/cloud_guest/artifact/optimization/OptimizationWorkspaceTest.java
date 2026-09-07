package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptimizationWorkspaceTest {
    @Test void savingRequiresCurrentVersionAndPreservesRoleBuildData() throws Exception {
        var store = mock(ArtifactJsonStore.class);
        var mapper = new ObjectMapper();
        when(store.get(eq("artifact-optimizer-workspace"),eq("100000001"),eq(com.fasterxml.jackson.databind.node.ObjectNode.class))).thenReturn(Optional.empty());
        var service = new OptimizationWorkspace(store,mapper);
        var input = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("""
                {"version":0,"characters":[{"key":"amber","name":"安柏","tags":["弓箭"],"weight":2,"protected":true,"builds":[{"id":"team","weight":1}]}],"builds":[]}
                """);
        var saved=service.save("100000001",input);
        assertEquals(1,saved.path("version").asLong());
        assertTrue(saved.path("characters").get(0).path("protected").asBoolean());
        when(store.get(eq("artifact-optimizer-workspace"),eq("100000001"),eq(com.fasterxml.jackson.databind.node.ObjectNode.class))).thenReturn(Optional.of(saved));
        assertThrows(IllegalStateException.class,()->service.save("100000001",input));
        assertEquals(0,input.path("version").asLong());
    }
}
