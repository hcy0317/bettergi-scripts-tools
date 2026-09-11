package com.cloud_guest.artifact.optimization;

import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GcsimInventoryCatalogTest {
    @TempDir Path root;

    @Test void catalogKeepsNativeOwnersSeparateAndRetainsEngineIdentity() throws Exception {
        var mapper=new ObjectMapper();
        var file=root.resolve("GameTask/AutoFight/Assets/combat_avatar.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file,"""
            [{"id":"10000021","name":"安柏","nameEn":"Amber","alias":["安柏"]},
             {"id":"10000133","name":"桑多涅","nameEn":"Marionette","alias":["桑多涅","Sandrone"]}]
            """);
        var sources=mock(CultivationMaterialSourceCatalog.class);
        when(sources.betterGiRoot()).thenReturn(root);
        var gateway=new GcsimGateway(mapper,sources,"") {
            @Override public JsonNode execute(String mode,JsonNode request,Duration timeout) throws Exception {
                return mapper.readTree("{\"engineRevision\":\""+"0".repeat(40)+"\",\"characters\":[{\"id\":10000021,\"key\":\"amber\"}]}");
            }
        };
        var catalog=gateway.catalog();
        assertEquals(1,catalog.path("characters").size());
        assertEquals(2,catalog.path("inventoryCharacters").size());
        var owners=new OptimizationOwnerResolver(catalog,mapper.readTree("{\"characters\":[]}"),Set.of("amber"));
        assertEquals("amber",owners.resolve("安柏"));
        assertEquals("marionette",owners.resolve("桑多涅"));
        assertEquals("marionette",owners.resolve("Sandrone"));
    }
}
