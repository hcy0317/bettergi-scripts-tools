package com.cloud_guest.artifact.optimization;

import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.cloud_guest.cultivation.ocr.CultivationOcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationStrategiesTest {
    @TempDir Path root;
    private OptimizationStrategies strategies(){
        var properties=new CultivationOcrProperties();properties.setBettergiRoot(root.toString());
        var mapper=new ObjectMapper();return new OptimizationStrategies(new CultivationMaterialSourceCatalog(properties,mapper),mapper);
    }
    @Test void discoversSharedStrategiesInSubfoldersWithoutRequiringABuild()throws Exception {
        Path directory=Files.createDirectories(root.resolve("User/AutoFight/群友分享"));
        Files.writeString(directory.resolve("安柏练习.txt"),"安柏 e,attack(2)");
        assertEquals(java.util.List.of("群友分享/安柏练习.txt"),strategies().list());
    }
    @Test void sourceApiKeepsGuardedSegmentsAndRejectsPathTraversal()throws Exception {
        Path directory=Files.createDirectories(root.resolve("User/AutoFight"));
        String source="strategy(loop=battle)\nsegment(爆发,define,requires=record-active(护盾)) {\n安柏 q(required,keep=护盾)\n}\nsegment(普攻,define) {\n安柏 attack(2)\n}\n";
        Files.writeString(directory.resolve("验证.txt"),"\uFEFF"+source);
        var service=strategies();var response=new com.cloud_guest.controller.ArtifactRotationController(null,service).source("验证.txt");
        assertNotNull(response);
        var detail=service.source("验证.txt");assertEquals(source,detail.path("originalScript").asText());
        assertEquals(3,detail.path("sections").size());assertTrue(detail.path("sections").get(1).path("source").asText().contains("requires=record-active(护盾)"));
        var compiler=new OptimizationRotationCompiler(new ObjectMapper());var aliases=java.util.Map.of("安柏","amber");
        assertFalse(compiler.parse(detail.path("sections").get(1).path("source").asText(),aliases).path("supported").asBoolean());
        assertTrue(compiler.parse(detail.path("sections").get(2).path("source").asText(),aliases).path("supported").asBoolean());
        assertThrows(IllegalArgumentException.class,()->service.source("../secret.txt"));
        assertThrows(IllegalArgumentException.class,()->service.source("C:/secret.txt"));
    }
    @Test void pastedJsonUsesSameCompatibilityAdapterWithoutDroppingDeclarations()throws Exception {
        var mapping=new OptimizationStrategies.Mapping(java.util.Map.of("安柏","amber"),java.util.Map.of());
        String json="{\"actions\":[{\"character\":\"安柏\",\"action\":\"e,attack(2)\"}]}";
        var parsed=strategies().parseSource(json,mapping);
        assertTrue(parsed.path("supported").asBoolean(),parsed.toString());assertEquals(json,parsed.path("originalScript").asText());
        assertFalse(strategies().parseSource("{\"actions\":[],\"branch\":\"条件\"}",mapping).path("supported").asBoolean());
        assertFalse(new OptimizationRotationCompiler(new ObjectMapper()).parse("segment(甲,define) {\n安柏 e\n}\nsegment(乙,define) {\n安柏 q\n}",mapping.aliases()).path("supported").asBoolean());
    }
}
