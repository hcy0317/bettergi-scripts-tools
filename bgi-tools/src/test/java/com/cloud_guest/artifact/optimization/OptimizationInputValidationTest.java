package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloud_guest.controller.ArtifactOptimizationValidationAdvice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationInputValidationTest {
    @Test void invalidPersonalCapsReturnFieldAddressableErrorsInsteadOfLaunchingWork()throws Exception {
        var mapper=new ObjectMapper();var workspace=mapper.readTree("""
          {"characters":[{"key":"amber","level":20,"maxLevel":35,"constellation":0,"weapon":"huntersbow","weaponLevel":20,"weaponMaxLevel":20,"refinement":1,"talents":[6,6,6],"builds":[]}],"builds":[]}
          """);
        var issues=OptimizationInputValidation.validate(mapper,workspace,mapper.readTree("{\"characters\":[\"amber\"]}"),null,null);
        assertTrue(issues.stream().anyMatch(i->i.character().equals("amber")&&i.field().equals("maxLevel")));
        assertTrue(issues.stream().anyMatch(i->i.field().equals("builds")));
        var result=new ArtifactOptimizationValidationAdvice().invalid(new OptimizationValidationException(issues));
        assertEquals(400,result.getStatusCode().value());assertEquals(issues,result.getBody().getData());
    }
}
