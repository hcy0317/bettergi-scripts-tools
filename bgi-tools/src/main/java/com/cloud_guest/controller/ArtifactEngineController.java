package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.OptimizationEngineUpdates;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import static com.cloud_guest.result.Result.ok;

@RestController
@RequestMapping("/jwt/artifacts/optimizer/engine")
public class ArtifactEngineController {
    private final OptimizationEngineUpdates updates;
    public ArtifactEngineController(OptimizationEngineUpdates updates){this.updates=updates;}
    @PostMapping("/check")public Result<ObjectNode> check()throws Exception{return ok(updates.check());}
    @PostMapping("/install")public Result<ObjectNode> install(@RequestBody ObjectNode input)throws Exception{return ok(updates.install(input.path("assetId").asLong(),input.path("confirmed").asBoolean()));}
    @PostMapping("/rollback")public Result<ObjectNode> rollback(@RequestBody ObjectNode input)throws Exception{return ok(updates.rollback(input.path("confirmed").asBoolean()));}
}
