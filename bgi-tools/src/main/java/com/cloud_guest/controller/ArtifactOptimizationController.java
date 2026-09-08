package com.cloud_guest.controller;

import com.cloud_guest.artifact.optimization.*;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobRepository;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.cloud_guest.result.Result.ok;

@RestController
@RequestMapping("/jwt/artifacts/optimizer")
public class ArtifactOptimizationController {
    private final OptimizationWorkspace workspace;
    private final OptimizationJobs jobs;
    private final GcsimGateway engine;
    private final OptimizationEnka enka;
    private final ArtifactAnalysisJobRepository scans;
    public ArtifactOptimizationController(OptimizationWorkspace workspace,OptimizationJobs jobs,GcsimGateway engine,OptimizationEnka enka,ArtifactAnalysisJobRepository scans){this.workspace=workspace;this.jobs=jobs;this.engine=engine;this.enka=enka;this.scans=scans;}
    @GetMapping("/workspace") public Result<ObjectNode> workspace(@RequestParam String uid){return ok(workspace.get(uid));}
    @PutMapping("/workspace") public Result<ObjectNode> save(@RequestParam String uid,@RequestBody ObjectNode value)throws Exception{
        boolean nativeEnabled=false;for(JsonNode build:value.path("builds"))if(build.path("nativeRotation").path("enabled").asBoolean())nativeEnabled=true;
        workspace.validateNativeDraft(value,nativeEnabled?engine.catalog():null);return ok(workspace.save(uid,value));
    }
    @GetMapping("/catalog") public Result<JsonNode> catalog() throws Exception{return ok(engine.catalog());}
    @PostMapping("/enka-preview") public Result<ObjectNode> enka(@RequestParam String uid) throws Exception{return ok(enka.preview(uid,engine.catalog()));}
    @GetMapping("/snapshots") public Result<List<Map<String,Object>>> snapshots(@RequestParam String uid){
        OptimizationWorkspace.requireUid(uid);
        return ok(scans.findByUid(uid).stream().filter(j->j.snapshot()!=null).limit(30).map(j->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",j.id());result.put("createdAt",j.createdAtUtc());result.put("count",j.snapshot().artifacts().size());result.put("gridCount",j.snapshot().artifactCount());result.put("complete",!j.snapshot().artifacts().isEmpty());result.put("scope","recognized_equippable_instances");result.put("snapshotDigest",j.snapshot().snapshotDigest());return result;}).toList());
    }
    @GetMapping("/snapshots/{id}") public Result<com.cloud_guest.artifact.domain.ArtifactSnapshot> snapshot(@RequestParam String uid,@PathVariable String id){OptimizationWorkspace.requireUid(uid);var scan=scans.findById(id).filter(j->j.uid().equals(uid)&&j.snapshot()!=null).orElseThrow(()->new IllegalArgumentException("快照不存在"));return ok(scan.snapshot());}
    @PostMapping("/jobs") public Result<ObjectNode> start(@RequestParam String uid,@RequestBody ObjectNode selection)throws Exception{return ok(jobs.start(uid,selection));}
    @GetMapping("/jobs") public Result<List<ObjectNode>> jobs(@RequestParam String uid){return ok(jobs.list(uid));}
    @GetMapping("/jobs/{id}") public Result<ObjectNode> job(@RequestParam String uid,@PathVariable String id){return ok(jobs.get(uid,id));}
    @PostMapping("/jobs/{id}/cancel") public Result<ObjectNode> cancel(@RequestParam String uid,@PathVariable String id){return ok(jobs.cancel(uid,id));}
}
