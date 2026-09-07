package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.*;
import com.cloud_guest.result.Result;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jwt/artifacts/optimizer/community")
public class ArtifactCommunityController {
    private final OptimizationCommunityService community;private final ObjectMapper mapper;
    public ArtifactCommunityController(OptimizationCommunityService community,ObjectMapper mapper){this.community=community;this.mapper=mapper;}
    @PostMapping("/search")public Result<ObjectNode> search(@RequestBody ObjectNode input)throws Exception{return Result.ok(community.search(input));}
    @PostMapping("/preview")public Result<ObjectNode> preview(@RequestBody ObjectNode input)throws Exception{return Result.ok(input.has("source")?new OptimizationCommunityImport(mapper).parse(input.path("source").asText()):community.read(input.path("reference").asText()));}
    @PostMapping("/outline")public Result<ArrayNode> outline(@RequestBody ObjectNode input){var result=mapper.createArrayNode();int index=0;for(var part:OptimizationScriptParts.split(input.path("source").asText()))if(part.code().matches("(?s)^(for|while)\\b.*"))result.addObject().put("index",++index).put("line",part.line());return Result.ok(result);}
}
