package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.OptimizationEquipmentPlans;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import static com.cloud_guest.result.Result.ok;

@RestController
@RequestMapping("/jwt/artifacts/optimizer/equipment")
public class ArtifactEquipmentController {
    private final OptimizationEquipmentPlans plans;
    public ArtifactEquipmentController(OptimizationEquipmentPlans plans){this.plans=plans;}
    @PostMapping("/preview")public Result<ObjectNode> preview(@RequestParam String uid,@RequestBody ObjectNode input)throws Exception{return ok(plans.preview(uid,input.path("jobId").asText()));}
    @PostMapping("/{id}/confirm")public Result<ObjectNode> confirm(@RequestParam String uid,@PathVariable String id,@RequestBody ObjectNode input)throws Exception{if(!input.path("acceptImpacts").asBoolean())throw new IllegalArgumentException("必须明确确认全部受影响角色和试算假设");return ok(plans.confirm(uid,id,input.path("digest").asText()));}
    @GetMapping("/{id}")public Result<ObjectNode> get(@RequestParam String uid,@PathVariable String id){return ok(plans.get(uid,id));}
    @PostMapping("/{id}/cancel")public Result<ObjectNode> cancel(@RequestParam String uid,@PathVariable String id){return ok(plans.cancel(uid,id));}
    @PostMapping("/{id}/recovery-preview")public Result<ObjectNode> recover(@RequestParam String uid,@PathVariable String id,@RequestBody ObjectNode input)throws Exception{return ok(plans.recover(uid,id,input.path("snapshotId").asText()));}
}
