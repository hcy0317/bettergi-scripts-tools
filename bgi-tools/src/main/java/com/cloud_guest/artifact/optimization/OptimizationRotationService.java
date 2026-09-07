package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.job.ArtifactAnalysisJobRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OptimizationRotationService {
    private final OptimizationWorkspace workspaces;
    private final ArtifactAnalysisJobRepository scans;
    private final OptimizationMainStats stats;
    private final GcsimGateway gateway;
    private final OptimizationJobs jobs;
    private final OptimizationStrategies strategies;
    private final ObjectMapper mapper;
    public OptimizationRotationService(OptimizationWorkspace w,ArtifactAnalysisJobRepository s,OptimizationMainStats m,GcsimGateway g,OptimizationJobs j,OptimizationStrategies strategies,ObjectMapper mapper){workspaces=w;scans=s;stats=m;gateway=g;jobs=j;this.strategies=strategies;this.mapper=mapper;}
    public ObjectNode start(String uid,ObjectNode input)throws Exception{
        var workspace=workspaces.get(uid);if(workspace.path("version").asLong()!=input.path("workspaceVersion").asLong(-1))throw new IllegalStateException("档案已变更，请保存后重新开始");
        String buildId=input.path("buildId").asText();var build=findBuild(workspace,buildId);
        var scan=scans.findById(input.path("snapshotId").asText()).filter(s->s.uid().equals(uid)&&s.snapshot()!=null).orElseThrow(()->new IllegalArgumentException("扫描记录不存在"));
        var catalog=gateway.catalog();var selection=input.deepCopy().put("mode","balanced").put("budget",256);
        if(!selection.path("characters").isArray()||selection.path("characters").isEmpty())throw new IllegalArgumentException("请选择有完整装备的参选角色");
        var compiled=new OptimizationCompiler(mapper,stats,catalog).compile(workspace,scan.snapshot(),selection);
        JsonNode scenario=null;for(JsonNode s:compiled.path("scenarios"))if(s.path("id").asText().equals(buildId))scenario=s;
        if(scenario==null)throw new IllegalArgumentException("本次选择没有引用该配队 Build");
        var memberKeys=new LinkedHashSet<String>();build.path("members").forEach(m->memberKeys.add(m.path("character").asText()));
        ObjectNode base=scenario.path("evaluation").deepCopy();String config=base.path("config").asText(),rotation=OptimizationLocalization.translateRotation(build.path("rotation").asText(),catalog,memberKeys);
        if(!config.endsWith(rotation))throw new IllegalStateException("循环模板身份不一致");
        base.put("config",config.substring(0,config.length()-rotation.length())+"__BETTERGI_ROTATION__");base.put("mainLoopIndex",0);base.set("inventory",compiled.path("inventory"));
        JsonNode outfit=null;String priorId=input.path("equipmentJobId").asText("");
        if(!priorId.isBlank()){
            var prior=jobs.frozen(uid,priorId);
            if(!prior.path("result").path("plan").path("qualified").asBoolean()||!prior.path("snapshotDigest").asText().equals(scan.snapshot().snapshotDigest())||prior.path("workspaceVersion").asLong()!=workspace.path("version").asLong())throw new IllegalArgumentException("配装方案已过期或不合格，请重新计算，或选择当前实装");
            outfit=prior.path("result").path("plan").path("equipment");
        }
        var idsByOwner=mapper.createObjectNode();scenario.path("fixedEquipment").fields().forEachRemaining(e->idsByOwner.set(e.getKey(),e.getValue()));
        var participants=new HashSet<String>();scenario.path("participants").forEach(v->participants.add(v.asText()));
        for(JsonNode c:compiled.path("characters")){String key=c.path("character").asText();if(!participants.contains(key))continue;JsonNode ids=outfit==null?c.path("current"):outfit.path(key);if(!ids.isArray()||ids.size()!=5)throw new IllegalArgumentException(key+" 当前没有完整五件装备，请先配装或扫描");idsByOwner.set(key,ids);}
        var byId=new HashMap<Integer,JsonNode>();compiled.path("items").forEach(item->byId.put(item.path("scanIndex").asInt(),item));var equipment=base.putObject("equipment");
        idsByOwner.fields().forEachRemaining(entry->{var pieces=equipment.putArray(entry.getKey());for(JsonNode id:entry.getValue()){JsonNode item=byId.get(id.asInt());if(item==null)throw new IllegalArgumentException("所选装备已不在该快照");ObjectNode piece=item.deepCopy();piece.remove(List.of("location","locked","fingerprint"));pieces.add(piece);}});
        var request=mapper.createObjectNode();request.set("base",base);request.set("actions",input.path("actions").deepCopy());request.set("searchSeeds",compiled.path("searchSeeds"));request.set("validationSeeds",compiled.path("validationSeeds"));request.put("budget",input.path("rotationBudget").asInt(48));
        if(request.path("budget").asInt()<4||request.path("budget").asInt()>512)throw new IllegalArgumentException("循环候选预算须为 4 至 512");
        int wall=input.path("wallTimeSeconds").asInt(120);var payload=mapper.createObjectNode();payload.set("rotation",request);payload.putObject("limits").put("wallTimeMs",wall*1000).put("memoryMiB",768).put("outputKiB",16384);
        var meta=mapper.createObjectNode().put("kind","rotation").put("buildId",buildId).put("snapshotId",scan.id()).put("snapshotDigest",scan.snapshot().snapshotDigest()).put("workspaceVersion",workspace.path("version").asLong()).put("engineRevision",catalog.path("engineRevision").asText());meta.set("request",request);meta.set("selection",input.deepCopy());return jobs.enqueue(uid,meta,payload,wall,"--rotation");
    }
    public ObjectNode importNative(String uid,String buildId,String name)throws Exception{return strategies.read(name,mapping(uid,buildId));}
    public ObjectNode parseNative(String uid,String buildId,String source)throws Exception{return strategies.parseSource(source,mapping(uid,buildId));}
    public ObjectNode nativePreview(String uid,String id,String preset)throws Exception{
        var job=jobs.frozen(uid,id);var report=job.path("result").path("report");
        if(!job.path("state").asText().equals("COMPLETED")||!report.path("validation").path("state").asText().equals("passed")||!report.path("validation").path("complete").asBoolean())throw new IllegalStateException("只有最终独立验证通过的循环才能生成执行候选");
        if(!report.path("assumptions").isEmpty()||report.path("support").asText().equals("trial"))throw new IllegalStateException("该循环依赖手动 Buff、外部能量或不完整机制，目前只可模拟参考，不能当作已核验的实机执行策略");
        if(job.path("workspaceVersion").asLong()!=workspaces.get(uid).path("version").asLong())throw new IllegalStateException("Build 已改变，请重新计算");
        String script=new OptimizationRotationCompiler(mapper).nativeScript(job.path("result").path("actions"),mapping(uid,job.path("buildId").asText()).names(),preset);
        var response=mapper.createObjectNode().put("script",script).put("preset",preset).put("simulationOnly",false).put("note","这是待确认的执行候选，使用现有运行器的等待、容错与重新判断；不会启动游戏或覆盖原策略。Buff 假设不等于实机已触发。");response.set("assumptions",report.path("assumptions"));return response;
    }
    private OptimizationStrategies.Mapping mapping(String uid,String id)throws Exception{var members=new LinkedHashSet<String>();findBuild(workspaces.get(uid),id).path("members").forEach(m->members.add(m.path("character").asText()));return strategies.mapping(gateway.catalog(),members);}
    private static JsonNode findBuild(JsonNode workspace,String id){for(JsonNode b:workspace.path("builds"))if(b.path("id").asText().equals(id))return b;throw new IllegalArgumentException("Build 不存在");}
}
