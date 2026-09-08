package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationOwnerResolverTest {
    @Test void protectionForAnUnknownLegacyProfileCannotDisappear()throws Exception{
        var mapper=new ObjectMapper();
        var workspace=mapper.readTree("{\"characters\":[{\"key\":\"unverified\",\"protected\":true}]}");
        var resolver=new OptimizationOwnerResolver(mapper.readTree("{\"characters\":[],\"inventoryCharacters\":[]}"),workspace,Set.of());
        assertThrows(IllegalArgumentException.class,()->resolver.protectedKeys(workspace));
    }

    @Test void knownInventoryOwnerDoesNotNeedASimulationImplementation() throws Exception {
        var mapper=new ObjectMapper();
        var catalog=mapper.readTree("""
            {"characters":[{"id":10000021,"key":"amber","inventoryAliases":["安柏"]}],
             "inventoryCharacters":[{"id":10000133,"key":"inventory10000133","nativeName":"桑多涅","inventoryAliases":["桑多涅","Sandrone"]}]}
            """);
        var resolver=new OptimizationOwnerResolver(catalog,mapper.readTree("{\"characters\":[]}"),Set.of("amber"));
        assertEquals("inventory10000133",resolver.resolve("桑多涅"));
        assertEquals("inventory10000133",resolver.resolve("Sandrone"));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve("未核实穿戴者"));
    }

    @Test void chineseOcrOwnersAndRenamedCharactersResolveWithoutUsingDisplayNames() throws Exception {
        var m=new ObjectMapper();
        var catalog=m.readTree("{\"characters\":[{\"id\":21,\"key\":\"amber\",\"inventoryAliases\":[\"安柏\"]},{\"id\":75,\"key\":\"wanderer\",\"inventoryAliases\":[\"流浪者\"]}]}");
        var workspace=m.readTree("{\"characters\":[{\"key\":\"amber\",\"name\":\"随便显示名\"},{\"key\":\"wanderer\",\"inventoryName\":\"阿散\"}]}");
        var resolver=new OptimizationOwnerResolver(catalog,workspace,Set.of("amber","wanderer"));
        assertEquals("amber",resolver.resolve("安柏"));assertEquals("wanderer",resolver.resolve("阿散"));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve("随便显示名"));
    }
}
