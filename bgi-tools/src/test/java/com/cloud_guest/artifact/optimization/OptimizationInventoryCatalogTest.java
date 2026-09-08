package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationInventoryCatalogTest {
    @Test void genericTravelerSharesOnePhysicalIdentityAndUnknownSentinelIsNotAPerson() throws Exception {
        var mapper=new ObjectMapper();
        var result=OptimizationInventoryCatalog.attach(mapper.readTree("""
            {"characters":[{"id":10000005,"key":"aetherdendro"},{"id":10000007,"key":"lumineanemo"}]}
            """),mapper.readTree("""
            [{"id":20000000,"name":"旅行者","alias":["空","荧"]},{"id":99999999,"name":"未知角色"}]
            """));
        assertEquals(1,result.path("inventoryCharacters").size());
        var resolver=new OptimizationOwnerResolver(result,mapper.createObjectNode(),Set.of("aetherdendro"));
        assertEquals("aetherdendro",resolver.resolve("旅行者"));
        assertEquals("aetherdendro",resolver.representative("inventory20000000"));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve("未知角色"));
        assertThrows(IllegalArgumentException.class,()->new OptimizationOwnerResolver(result,mapper.createObjectNode(),Set.of("aetherdendro","lumineanemo")));
    }

    @Test void supplementsPhysicalIdentitiesWithoutInventingSimulationCharacters() throws Exception {
        var mapper=new ObjectMapper();
        var engine=mapper.readTree("""
            {"characters":[{"id":10000021,"key":"amber","icon_name":"AmberIcon"}]}
            """);
        var nativeData=mapper.readTree("""
            [{"id":"10000021","name":"安柏","nameEn":"Amber","alias":["侦察骑士"]},
             {"id":"10000133","name":"桑多涅","nameEn":"Marionette","alias":["Sandrone"]}]
            """);
        var result=OptimizationInventoryCatalog.attach(engine,nativeData);
        assertEquals(1,result.path("characters").size());
        assertEquals(2,result.path("inventoryCharacters").size());
        assertFalse(result.path("inventoryCharacters").get(1).path("hasSimulationEntry").asBoolean());
        assertEquals("inventory10000021",result.path("characters").get(0).path("inventoryKey").asText());
        assertFalse(engine.has("inventoryCharacters"));
        var resolver=new OptimizationOwnerResolver(result,mapper.createObjectNode(),Set.of("amber"));
        assertEquals("amber",resolver.resolve("侦察骑士"));
        assertEquals("amber",resolver.representative("inventory10000021"));
        assertEquals("inventory10000133",resolver.resolve("Sandrone"));
        assertEquals("桑多涅",resolver.nativeName("inventory10000133"));
    }
}
