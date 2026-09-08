package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobRepository;
import com.cloud_guest.artifact.launch.*;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class OptimizationEquipmentPlans {
    private static final String TYPE="artifact-equipment-plan";
    private final ArtifactJsonStore store;
    private final OptimizationJobs jobs;
    private final OptimizationWorkspace workspaces;
    private final ArtifactAnalysisJobRepository scans;
    private final ArtifactLaunchRequestService launches;
    private final GcsimGateway gateway;
    private final ObjectMapper mapper;
    public OptimizationEquipmentPlans(ArtifactJsonStore store,OptimizationJobs jobs,OptimizationWorkspace workspaces,ArtifactAnalysisJobRepository scans,ArtifactLaunchRequestService launches,GcsimGateway gateway,ObjectMapper mapper){this.store=store;this.jobs=jobs;this.workspaces=workspaces;this.scans=scans;this.launches=launches;this.gateway=gateway;this.mapper=mapper;}
    public synchronized ObjectNode preview(String uid,String jobId)throws Exception{
        var job=jobs.frozen(uid,jobId);var workspace=workspaces.get(uid);
        if(!job.path("state").asText().equals("COMPLETED")||!job.path("result").path("plan").path("qualified").asBoolean())throw new IllegalStateException("没有通过独立验证的配装方案");
        if(job.path("workspaceVersion").asLong()!=workspace.path("version").asLong())throw new IllegalStateException("角色、保护或 Build 已改变，请重新计算");
        var catalog=gateway.catalog();if(!catalog.path("engineRevision").asText().equals(job.path("engineRevision").asText()))throw new IllegalStateException("引擎版本已变化，请重新计算");
        var scan=scans.findById(job.path("snapshotId").asText()).filter(j->j.uid().equals(uid)&&j.snapshot()!=null).orElseThrow(()->new IllegalStateException("原计算快照不存在"));
        var plan=base(uid,jobId,workspace,scan.snapshot());var targets=plan.putArray("targets");
        job.path("result").path("plan").path("equipment").fields().forEachRemaining(e->{
            JsonNode profile=find(workspace.path("characters"),"key",e.getKey()),character=find(catalog.path("characters"),"key",e.getKey());
            String nativeName=character.path("nativeName").asText("");if(nativeName.isBlank())throw new IllegalStateException("缺少 BetterGI 角色身份映射："+e.getKey());
            String inventoryName=profile.path("inventoryName").asText("");if(inventoryName.isBlank())inventoryName=nativeName;
            if(e.getValue().size()!=5)throw new IllegalStateException("每个参选角色必须有五件实物");
            var target=targets.addObject().put("character",e.getKey()).put("nativeName",nativeName).put("inventoryName",inventoryName);target.set("artifacts",e.getValue().deepCopy());
        });
        addProtectedOwners(plan,workspace,catalog);
        plan.set("impacts",job.path("result").path("impacts").deepCopy());var assumptions=plan.putArray("assumptions");var seen=new HashSet<String>();job.path("result").path("plan").path("reports").forEach(r->r.path("assumptions").forEach(a->{if(seen.add(a.asText()))assumptions.add(a.asText());}));
        return seal(plan);
    }
    private ObjectNode base(String uid,String jobId,JsonNode workspace,ArtifactSnapshot snapshot){
        var plan=mapper.createObjectNode().put("id",UUID.randomUUID().toString()).put("uid",uid).put("sourceJobId",jobId).put("state","PREVIEW").put("confirmed",false).put("createdAt",Instant.now().toString()).put("workspaceVersion",workspace.path("version").asLong()).put("inventoryCount",snapshot.artifactCount());
        plan.set("snapshot",mapper.valueToTree(snapshot.artifacts()));plan.putArray("protectedOwners");
        plan.put("notice","确认范围包含目标角色、全部出借角色、被卸下的旧装和等价副本数量。游戏若自动互换，出借角色可能收到被替换装备。不会改变锁定状态；不保证跨界面原子完成，失败后必须重新观察。");return plan;
    }
    private void addProtectedOwners(ObjectNode plan,JsonNode workspace,JsonNode catalog){
        var resolver=new OptimizationOwnerResolver(catalog,workspace,Set.of());
        var protectedKeys=resolver.protectedKeys(workspace);
        if(protectedKeys.isEmpty())return;
        var names=new LinkedHashSet<String>();
        for(String key:protectedKeys)names.add(resolver.inventoryName(key));
        // Include the exact observed aliases as well as the current game name.
        // The host consumes those observed names; aliases must not bypass protection.
        for(JsonNode item:plan.path("snapshot")){
            String owner=item.path("location").asText();
            if(!owner.isBlank()&&protectedKeys.contains(resolver.resolve(owner)))names.add(owner);
        }
        names.forEach(name->((ArrayNode)plan.path("protectedOwners")).add(name));
    }
    private ObjectNode seal(ObjectNode plan){
        var protectedNames=new HashSet<String>();plan.path("protectedOwners").forEach(n->protectedNames.add(n.asText()));
        for(JsonNode target:plan.path("targets"))for(JsonNode id:target.path("artifacts")){String owner=find(plan.path("snapshot"),"scanIndex",id.asText()).path("location").asText(),destination=target.path("inventoryName").asText();if(!owner.equals(destination)&&(protectedNames.contains(owner)||protectedNames.contains(destination)))throw new IllegalStateException("恢复/穿戴与当前装备保护冲突，请先明确调整保护再重新预览");}
        var expected=new HashSet<Integer>();var affected=new TreeSet<String>();
        for(JsonNode target:plan.path("targets")){affected.add(target.path("inventoryName").asText());for(JsonNode id:target.path("artifacts")){if(!expected.add(id.asInt()))throw new IllegalArgumentException("实物重复分配");var item=find(plan.path("snapshot"),"scanIndex",id.asText());String owner=item.path("location").asText();if(!owner.isBlank())affected.add(owner);}}
        plan.set("affectedOwners",mapper.valueToTree(affected));var original=plan.putObject("originalEquipment");for(String owner:affected){var ids=original.putArray(owner);for(JsonNode item:plan.path("snapshot"))if(item.path("location").asText().equals(owner))ids.add(item.path("scanIndex").asInt());}
        plan.put("digest",digest(plan.toString()));store.put(TYPE,key(plan.path("uid").asText(),plan.path("id").asText()),plan);return view(plan);
    }
    public synchronized ObjectNode confirm(String uid,String id,String digest)throws Exception{
        var plan=stored(uid,id);if(!plan.path("digest").asText().equals(digest))throw new IllegalArgumentException("确认内容与预览不一致");
        if(!plan.path("state").asText().equals("PREVIEW"))throw new IllegalStateException("该确认已使用或不再有效，请查看执行状态");
        if(plan.path("workspaceVersion").asLong()!=workspaces.get(uid).path("version").asLong())throw new IllegalStateException("档案/保护已改变，请重新预览");
        plan.put("confirmed",true).put("state","CONFIRMED");store.put(TYPE,key(uid,id),plan);
        try{var launch=launches.create(uid,id,ArtifactLaunchOperation.EXECUTE_EQUIP_PLAN,plan.path("inventoryCount").asInt(),List.of(),null,digest);plan.set("launch",mapper.valueToTree(launch));store.put(TYPE,key(uid,id),plan);return view(plan);}
        catch(Exception error){plan.put("state","LAUNCH_UNCERTAIN").put("error",Objects.toString(error.getMessage(),"launch failed"));store.put(TYPE,key(uid,id),plan);throw error;}
    }
    public synchronized ObjectNode claim(String uid,String id,String token){
        var plan=stored(uid,id);if(!plan.path("confirmed").asBoolean()||!plan.path("state").asText().equals("CONFIRMED"))throw new IllegalStateException("穿戴计划未确认、已领取或需要重新观察，禁止重放");
        launches.consume(token,uid,id,ArtifactLaunchOperation.EXECUTE_EQUIP_PLAN);plan.put("state","RUNNING");store.put(TYPE,key(uid,id),plan);return plan;
    }
    public synchronized ObjectNode progress(String uid,String id,String token,ObjectNode report){
        launches.authorizeClaimed(token,uid,id,ArtifactLaunchOperation.EXECUTE_EQUIP_PLAN);var plan=stored(uid,id);
        if(!Set.of("RUNNING","NEEDS_OBSERVATION").contains(plan.path("state").asText()))throw new IllegalStateException("穿戴任务状态已结束");
        if(report.toString().length()>4_000_000)throw new IllegalArgumentException("执行观察超过限制");
        plan.set("execution",report.deepCopy());plan.put("updatedAt",Instant.now().toString());String status=report.path("status").asText();
        if(status.equals("completed"))plan.put("state","COMPLETED");else if(!status.equals("running"))plan.put("state","NEEDS_OBSERVATION");store.put(TYPE,key(uid,id),plan);
        if(!status.equals("running"))launches.complete(token,uid,id,ArtifactLaunchOperation.EXECUTE_EQUIP_PLAN);
        return mapper.createObjectNode().put("cancelRequested",plan.path("cancelRequested").asBoolean());
    }
    public synchronized ObjectNode get(String uid,String id){return view(stored(uid,id));}
    public synchronized ObjectNode cancel(String uid,String id){var plan=stored(uid,id);plan.put("cancelRequested",true);if(Set.of("PREVIEW","CONFIRMED").contains(plan.path("state").asText()))plan.put("state","CANCELLED");store.put(TYPE,key(uid,id),plan);return view(plan);}
    public synchronized ObjectNode hostStatus(String uid,String id,String token){launches.authorizeClaimed(token,uid,id,ArtifactLaunchOperation.EXECUTE_EQUIP_PLAN);var p=stored(uid,id);return mapper.createObjectNode().put("cancelRequested",p.path("cancelRequested").asBoolean());}
    public synchronized ObjectNode recover(String uid,String id,String observedScanId)throws Exception{
        var latest=stored(uid,id);var original=latest.has("recoveryRoot")?stored(uid,latest.path("recoveryRoot").asText()):latest;
        if(Set.of("RUNNING","CONFIRMED").contains(latest.path("state").asText()))throw new IllegalStateException("请先安全停止当前执行，再观察和恢复");
        var scan=scans.findById(observedScanId).filter(s->s.uid().equals(uid)&&s.snapshot()!=null).orElseThrow(()->new IllegalArgumentException("请选择重新扫描的观察记录"));
        if(Instant.parse(scan.updatedAtUtc()).isBefore(Instant.parse(latest.path("updatedAt").asText(latest.path("createdAt").asText()))))throw new IllegalArgumentException("恢复需要执行之后的新观察，不能重放旧步骤");
        var workspace=workspaces.get(uid);var catalog=gateway.catalog();var active=new TreeSet<String>();original.path("targets").forEach(t->active.add(t.path("character").asText()));
        var resolver=new OptimizationOwnerResolver(catalog,workspace,active);
        var plan=base(uid,original.path("sourceJobId").asText(),workspace,scan.snapshot());plan.put("recoveryOf",id).put("recoveryRoot",original.path("id").asText());var targets=plan.putArray("targets");
        var available=new ArrayList<>(scan.snapshot().artifacts());
        for(var owners=original.path("originalEquipment").fields();owners.hasNext();){
            var entry=owners.next();String owner=entry.getKey();var oldIds=entry.getValue();
            if(oldIds.size()!=5)throw new IllegalStateException("角色 "+owner+" 原来存在空槽位，请先手动还原这些空槽位并重新扫描；不会伪造自动恢复完成");
        }
        for(var owners=original.path("originalEquipment").fields();owners.hasNext();){var entry=owners.next();String character=resolver.resolve(entry.getKey());String nativeName=resolver.nativeName(character);var target=targets.addObject().put("character",character).put("nativeName",nativeName).put("inventoryName",entry.getKey());var ids=target.putArray("artifacts");
            for(JsonNode idNode:entry.getValue()){
                ArtifactItem old=mapper.treeToValue(find(original.path("snapshot"),"scanIndex",idNode.asText()),ArtifactItem.class);
                var matches=available.stream().filter(a->physical(a).equals(physical(old))&&a.locked()==old.locked()).toList();if(matches.isEmpty())throw new IllegalStateException("原装内容、锁定或副本数量已变化，无法完整恢复");var chosen=matches.get(0);available.remove(chosen);ids.add(chosen.scanIndex());
            }
        }
        addProtectedOwners(plan,workspace,catalog);
        return seal(plan);
    }
    private static String physical(ArtifactItem i){return new ArtifactItem(i.scanIndex(),i.setKey(),i.slotKey(),i.level(),i.rarity(),i.mainStatKey(),i.substats(),"",i.locked()).contentFingerprint();}
    private static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private ObjectNode stored(String uid,String id){return store.get(TYPE,key(uid,id),ObjectNode.class).map(ObjectNode::deepCopy).orElseThrow(()->new IllegalArgumentException("穿戴计划不存在"));}
    private static String key(String uid,String id){OptimizationWorkspace.requireUid(uid);if(!OptimizationWorkspace.key(id))throw new IllegalArgumentException("计划标识无效");return uid+":"+id;}
    private static JsonNode find(JsonNode nodes,String field,String value){for(JsonNode n:nodes)if(n.path(field).asText().equals(value))return n;throw new IllegalArgumentException("计划引用缺失："+value);}
    private static ObjectNode view(ObjectNode plan){var result=plan.deepCopy();result.remove("snapshot");return result;}
}
