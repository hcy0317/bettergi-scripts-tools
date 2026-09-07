package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.*;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.cloud_guest.result.Result.ok;

@RestController
@RequestMapping("/jwt/artifacts/optimizer/rotations")
public class ArtifactRotationController {
    private final OptimizationRotationService service;
    private final OptimizationStrategies strategies;
    public ArtifactRotationController(OptimizationRotationService service,OptimizationStrategies strategies){this.service=service;this.strategies=strategies;}
    @GetMapping("/strategies")public Result<List<String>> strategies()throws Exception{return ok(strategies.list());}
    @PostMapping("/import")public Result<ObjectNode> load(@RequestParam String uid,@RequestBody ObjectNode input)throws Exception{return ok(input.has("source")?service.parseNative(uid,input.path("buildId").asText(),input.path("source").asText()):service.importNative(uid,input.path("buildId").asText(),input.path("name").asText()));}
    @PostMapping("/jobs")public Result<ObjectNode> start(@RequestParam String uid,@RequestBody ObjectNode input)throws Exception{return ok(service.start(uid,input));}
    @PostMapping("/jobs/{id}/preview")public Result<ObjectNode> preview(@RequestParam String uid,@PathVariable String id,@RequestParam String preset)throws Exception{return ok(service.nativePreview(uid,id,preset));}
}
