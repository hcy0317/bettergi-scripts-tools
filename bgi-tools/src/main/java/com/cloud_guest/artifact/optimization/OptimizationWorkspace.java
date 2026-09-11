package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class OptimizationWorkspace {
    private static final String TYPE="artifact-optimizer-workspace";
    private final ArtifactJsonStore store;
    private final ObjectMapper mapper;
    public OptimizationWorkspace(ArtifactJsonStore store,ObjectMapper mapper) { this.store=store;this.mapper=mapper; }
    public ObjectNode get(String uid) {
        requireUid(uid);
        var workspace=store.get(TYPE,uid,ObjectNode.class).map(ObjectNode::deepCopy).orElseGet(()->{
            var node=mapper.createObjectNode().put("version",0);node.putArray("characters");node.putArray("builds");return node;
        });
        for(JsonNode item:workspace.path("builds"))if(item instanceof ObjectNode build){
            if(!build.path("stopMode").asText().equals("loop_count")&&!build.has("legacyStopMode"))build.put("legacyStopMode",build.path("stopMode").asText("fixed_duration"));
            build.put("stopMode","loop_count");
            if(!build.has("roundCount"))build.put("roundCount",3);
            if(build.path("rounds").isArray()&&!build.path("rounds").isEmpty()&&!build.has("legacyRounds"))build.set("legacyRounds",build.path("rounds").deepCopy());
            build.putArray("rounds");
            ObjectNode policy=build.path("roundPolicy") instanceof ObjectNode p?p:build.putObject("roundPolicy");
            policy.put("mode","auto");if(!policy.has("warmup"))policy.put("warmup",0);if(!policy.has("loopIndex"))policy.put("loopIndex",0);
        }
        return workspace; // A read projection, never a database migration/write.
    }
    public synchronized ObjectNode save(String uid,ObjectNode incoming) {
        var current=get(uid);
        if (!incoming.path("version").canConvertToLong() || incoming.path("version").asLong()!=current.path("version").asLong()) {
            throw new IllegalStateException("配装档案已改变，请重新载入后合并编辑");
        }
        if (!incoming.path("characters").isArray() || !incoming.path("builds").isArray()
                || incoming.path("characters").size()>200 || incoming.path("builds").size()>300
                || incoming.toString().length()>2_000_000) throw new IllegalArgumentException("配装档案格式或大小无效");
        var protections=incoming.path("protectedInventoryOwners");
        if(!protections.isMissingNode()&&(!protections.isArray()||protections.size()>200))
            throw new IllegalArgumentException("库存角色保护列表格式或大小无效");
        for(var owner:protections)if(!owner.isTextual()||!key(owner.asText()))
            throw new IllegalArgumentException("库存保护角色标识无效");
        var keys=new java.util.HashSet<String>();
        incoming.path("characters").forEach(c->{if(!key(c.path("key").asText()) || !keys.add(c.path("key").asText())) throw new IllegalArgumentException("角色键无效或重复");});
        keys.clear();
        incoming.path("builds").forEach(b->{if(!key(b.path("id").asText()) || !keys.add(b.path("id").asText())) throw new IllegalArgumentException("Build 标识无效或重复");});
        var saved=incoming.deepCopy().put("version",current.path("version").asLong()+1).put("updatedAt",Instant.now().toString());
        if(current.path("version").asLong()>0) store.put(TYPE+"-previous",uid,current);
        store.put(TYPE,uid,saved);
        return saved.deepCopy();
    }
    public void validateNativeDraft(ObjectNode incoming,JsonNode catalog) {
        for(JsonNode build:incoming.path("builds")) {
            JsonNode nativeInput=build.path("nativeRotation");if(nativeInput.isMissingNode())continue;
            if(!nativeInput.isObject()||!nativeInput.path("enabled").isBoolean())throw OptimizationValidationException.scene(build.path("id").asText(),"rotation","原生流程状态格式无效");
            if(nativeInput.path("enabled").asBoolean()) {
                var members=new java.util.LinkedHashSet<String>();build.path("members").forEach(m->members.add(m.path("character").asText()));
                try{OptimizationNativeFlow.fromBuild(mapper,build,catalog,members);}catch(IllegalArgumentException error){throw OptimizationValidationException.scene(build.path("id").asText(),"rotation",error.getMessage());}
            }
        }
    }
    public static boolean key(String key) {return key!=null&&key.matches("[a-zA-Z0-9_-]{1,100}");}
    public static void requireUid(String uid) {if(uid==null||!uid.matches("[0-9]{6,12}"))throw new IllegalArgumentException("UID 无效");}
}
