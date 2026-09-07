package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.OptimizationEquipmentPlans;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.*;
import static com.cloud_guest.result.Result.ok;

@Hidden
@RestController
@RequestMapping("/artifacts/optimizer/host/plans/{id}")
public class ArtifactEquipmentHostController {
    private final OptimizationEquipmentPlans plans;
    public ArtifactEquipmentHostController(OptimizationEquipmentPlans plans){this.plans=plans;}
    @PostMapping("/claim")public Result<ObjectNode> claim(@PathVariable String id,@RequestParam String uid,@RequestParam String requestToken){return ok(plans.claim(uid,id,requestToken));}
    @PostMapping("/progress")public Result<ObjectNode> progress(@PathVariable String id,@RequestParam String uid,@RequestParam String requestToken,@RequestBody ObjectNode report){return ok(plans.progress(uid,id,requestToken,report));}
    @PostMapping("/status")public Result<ObjectNode> status(@PathVariable String id,@RequestParam String uid,@RequestParam String requestToken){return ok(plans.hostStatus(uid,id,requestToken));}
}
