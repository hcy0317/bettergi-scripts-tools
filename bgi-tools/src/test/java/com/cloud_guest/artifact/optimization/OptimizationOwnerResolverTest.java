package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationOwnerResolverTest {
    @Test void chineseOcrOwnersAndRenamedCharactersResolveWithoutUsingDisplayNames() throws Exception {
        var m=new ObjectMapper();
        var catalog=m.readTree("{\"characters\":[{\"id\":21,\"key\":\"amber\",\"inventoryAliases\":[\"安柏\"]},{\"id\":75,\"key\":\"wanderer\",\"inventoryAliases\":[\"流浪者\"]}]}");
        var workspace=m.readTree("{\"characters\":[{\"key\":\"amber\",\"name\":\"随便显示名\"},{\"key\":\"wanderer\",\"inventoryName\":\"阿散\"}]}");
        var resolver=new OptimizationOwnerResolver(catalog,workspace,Set.of("amber","wanderer"));
        assertEquals("amber",resolver.resolve("安柏"));assertEquals("wanderer",resolver.resolve("阿散"));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve("随便显示名"));
    }
}
