package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import com.cloud_guest.artifact.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationNativeFlowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    static final Map<String,String> ALIASES = Map.of("钟离","zhongli","芙宁娜","furina","那维莱特","neuvillette","琴","jean");
    static String water() throws Exception {
        try(var stream=OptimizationNativeFlowTest.class.getResourceAsStream("/artifact-optimizer/native-water.txt")) {
            assertNotNull(stream); return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void importsWaterControlFlowWithoutFlatteningItsGuardsOrMacro() throws Exception {
        String source=water();
        var parsed=new OptimizationRotationCompiler(mapper).parse(source,ALIASES);
        assertTrue(parsed.path("supported").asBoolean(),parsed.toPrettyString());
        assertEquals("native_flow",parsed.path("mode").asText());
        var program=parsed.path("program");
        assertEquals(source,program.path("source").asText());
        assertEquals(7,program.path("blocks").size());
        assertTrue(program.path("loop").asBoolean());
        var branch=java.util.stream.StreamSupport.stream(program.path("root").spliterator(),false).filter(n->n.path("kind").asText().equals("branch")).findFirst().orElseThrow();
        assertEquals("Q双喷",branch.path("options").path("then").asText());
        assertEquals("E单喷",branch.path("options").path("unknown").asText());
        assertEquals("neuvillette_charge_v1",program.path("blocks").path("喷射宏").path("macro").asText());
        assertTrue(program.path("blocks").path("喷射宏").path("nodes").size()>40);
        assertTrue(parsed.path("simulationOnly").asBoolean());
    }
    @Test void rejectsUnboundRecordAtItsSourceInsteadOfSilentlySkippingARequiredBranch() {
        var parsed=new OptimizationRotationCompiler(mapper).parse("strategy(loop=battle)\nbranch(if=record-active(不存在),then=动作)\nsegment(动作,define) {\n琴 e\n}",ALIASES);
        assertFalse(parsed.path("supported").asBoolean());
        assertTrue(parsed.path("issues").toString().contains("第2行"));
        assertTrue(parsed.path("issues").toString().contains("不存在"));
    }
    @Test void rejectsADeepFlatConditionBeforeSerializationOrEngineExecution() {
        String condition=String.join(" || ",java.util.Collections.nCopies(40,"q-ready(琴)"));
        var parsed=new OptimizationRotationCompiler(mapper).parse("strategy(loop=battle)\nbranch(if="+condition+",then=动作)\nsegment(动作,define){\n琴 e\n}",ALIASES);
        assertFalse(parsed.path("supported").asBoolean());
        assertTrue(parsed.path("issues").toString().contains("第2行"));
    }
    @Test void nativeBuildCompilesForSharedInventoryWithoutRequiringADuplicateGcsimScript() throws Exception {
        var workspace=mapper.createObjectNode();var profiles=workspace.putArray("characters");var builds=workspace.putArray("builds");
        var catalog=mapper.createObjectNode();var characters=catalog.putArray("characters");catalog.putObject("capabilities").putObject("nativeFlow").put("schemaVersion","native-flow-v1");
        var build=builds.addObject().put("id","water").put("duration",40).put("rotation","");var members=build.putArray("members");
        int id=1;for(var entry:ALIASES.entrySet()) {
            String key=entry.getValue();var profile=profiles.addObject().put("key",key).put("level",90).put("maxLevel",90).put("constellation",0).put("weapon","testweapon").put("weaponLevel",90).put("weaponMaxLevel",90).put("refinement",1);
            profile.putArray("talents").add(6).add(6).add(6);profile.putArray("builds").addObject().put("id","water");members.addObject().put("character",key);
            characters.addObject().put("key",key).put("id",id++).put("nativeName",entry.getKey());
        }
        build.putObject("roundPolicy").put("mode","auto");
        build.putObject("nativeRotation").put("enabled",true).put("source",water()).put("macroMapping",OptimizationNativeFlow.MACRO);
        var selection=mapper.createObjectNode();var selected=selection.putArray("characters");ALIASES.values().forEach(selected::add);
        var snapshot=ArtifactSnapshot.create("100000001","native","default","v1",List.of(new ArtifactItem(0,"EmblemOfSeveredFate","flower",20,5,"hp",List.of(),"",false)));
        var compiled=new OptimizationCompiler(mapper,(a,b,c)->4780,catalog).compile(workspace,snapshot,selection);
        assertEquals(1,compiled.path("scenarios").size());
        var evaluation=compiled.path("scenarios").get(0).path("evaluation");
        assertEquals(7,evaluation.path("nativeFlow").path("blocks").size());
        assertTrue(evaluation.path("autoRounds").asBoolean());
        assertTrue(evaluation.path("config").asText().contains("active zhongli;"));
        assertEquals("",build.path("rotation").asText());
    }
    @Test void referenceCandidateChangesOnlyTheBoundNumericSpanAndKeepsTheWholeMacro() throws Exception {
        String source=water();var adapter=new OptimizationNativeFlow(mapper,source,ALIASES);var program=adapter.program();
        var wait=program.path("blocks").path("E单喷").path("nodes").get(1);
        var changes=mapper.createArrayNode();changes.addObject().put("node",wait.path("id").asText()).put("original",0.65).put("value",0.4);
        String candidate=adapter.referenceSource(changes);
        assertEquals(source.replace("wait(0.65)","wait(0.4)"),candidate);
        assertEquals(source.substring(source.indexOf("segment(喷射宏")),candidate.substring(candidate.indexOf("segment(喷射宏")));
        changes.removeAll();var macroWait=program.path("blocks").path("喷射宏").path("nodes").get(1);
        changes.addObject().put("node",macroWait.path("id").asText()).put("original",1.6).put("value",1.4);
        assertThrows(IllegalArgumentException.class,()->adapter.referenceSource(changes));
    }
}
