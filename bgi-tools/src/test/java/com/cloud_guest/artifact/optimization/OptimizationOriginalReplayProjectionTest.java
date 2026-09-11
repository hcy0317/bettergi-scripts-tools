package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationOriginalReplayProjectionTest {
    @Test void completeOriginalBatchPassesThroughTheJavaGateway()throws Exception {
        String input=System.getProperty("artifact.optimizer.test.replayRequest","");
        String executable=System.getProperty("artifact.optimizer.test.executable","");
        Assumptions.assumeFalse(input.isBlank()||executable.isBlank(),"optional private full-batch replay");
        var m=new ObjectMapper();var request=m.readTree(Path.of(input).toFile());
        assertEquals(1145,request.path("items").size());assertEquals(3,request.path("scenarios").size());
        assertEquals(1000,request.path("validationSeeds").size());
        var payload=m.createObjectNode();payload.set("optimization",request);
        payload.putObject("limits").put("wallTimeMs",120000).put("memoryMiB",768).put("outputKiB",GcsimGateway.outputBudgetKiB(request));
        var response=new GcsimGateway(m,null,executable).execute("--optimize",payload,java.time.Duration.ofSeconds(125));
        assertEquals("completed",response.path("status").asText());
        var plan=response.path("result").path("plan");assertTrue(plan.path("qualified").asBoolean());
        assertEquals(3,plan.path("reports").size());
        for(var report:plan.path("reports")){
            assertEquals(1000,report.path("scoredDps").size());
            assertEquals(1000,report.path("sampleMetrics").size());
        }
        System.out.println("Full original batch verified: "+response.path("result").path("status").asText()+", outputBytes="+m.writeValueAsBytes(response).length+", resources="+response.path("resources"));
    }
    @Test void projectOriginalRequestWithTheProductionAdapter()throws Exception {
        String input=System.getProperty("artifact.optimizer.test.originalRequest","");
        String output=System.getProperty("artifact.optimizer.test.projectedRequest","");
        Assumptions.assumeFalse(input.isBlank()||output.isBlank(),"optional private original-request replay");
        var m=new ObjectMapper();ObjectNode original=(ObjectNode)m.readTree(Path.of(input).toFile());
        ObjectNode projected=original.deepCopy();int adaptations=0;
        for(var scenario:projected.path("scenarios")){
            var evaluation=(ObjectNode)scenario.path("evaluation");String config=evaluation.path("config").asText();
            var weapons=new HashMap<String,String>();
            var matcher=Pattern.compile("(?m)^([a-z0-9]+)\\s+add\\s+weapon=\"([a-z0-9]+)\"").matcher(config);
            while(matcher.find())weapons.put(matcher.group(1),matcher.group(2));
            var result=OptimizationScriptParts.adaptImpossibleWeaponWaits(config,weapons);
            OptimizationScriptParts.requireCompatibleWeaponWaits(result.config(),scenario.path("id").asText(),weapons);
            evaluation.put("config",result.config());result.assumptions().forEach(evaluation.withArray("assumptions")::add);
            adaptations+=result.assumptions().size();
        }
        assertEquals(1145,projected.path("items").size());assertEquals(3,projected.path("scenarios").size());assertEquals(1000,projected.path("validationSeeds").size());
        assertEquals(original.path("items"),projected.path("items"));assertEquals(original.path("characters"),projected.path("characters"));
        assertEquals(original.path("searchSeeds"),projected.path("searchSeeds"));assertEquals(original.path("validationSeeds"),projected.path("validationSeeds"));
        assertEquals(original.path("evaluationBudget"),projected.path("evaluationBudget"));assertTrue(adaptations>0);
        Files.createDirectories(Path.of(output).getParent());m.writeValue(Path.of(output).toFile(),projected);
    }
}
