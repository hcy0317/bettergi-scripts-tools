package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.job.ArtifactAnalysisJobRepository;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class OptimizationJobs {
    private static final String TYPE="artifact-optimizer-job";
    private final ArtifactJsonStore store;
    private final ArtifactAnalysisJobRepository scans;
    private final OptimizationWorkspace workspaces;
    private final OptimizationMainStats stats;
    private final GcsimGateway gateway;
    private final ObjectMapper mapper;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"gcsim-optimization");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private final Map<String,FutureTask<Void>> active=new HashMap<>();
    public OptimizationJobs(ArtifactJsonStore store,ArtifactAnalysisJobRepository scans,OptimizationWorkspace workspaces,OptimizationMainStats stats,GcsimGateway gateway,ObjectMapper mapper){
        this.store=store;this.scans=scans;this.workspaces=workspaces;this.stats=stats;this.gateway=gateway;this.mapper=mapper;
    }
    public synchronized ObjectNode start(String uid,ObjectNode selection) throws Exception {
        OptimizationWorkspace.requireUid(uid);
        if(active.size()>=3)throw new IllegalStateException("计算队列已满，请等待或取消旧任务");
        var workspace=workspaces.get(uid);
        if(selection.path("workspaceVersion").asLong(-1)!=workspace.path("version").asLong())throw new IllegalStateException("档案已改变，请保存并重新计算");
        var scan=scans.findById(selection.path("snapshotId").asText()).orElseThrow(()->new IllegalArgumentException("扫描记录不存在"));
        if(!scan.uid().equals(uid)||scan.snapshot()==null)throw new IllegalArgumentException("扫描记录与账号不匹配");
        var catalog=gateway.catalog();
        var request=new OptimizationCompiler(mapper,stats,catalog).compile(workspace,scan.snapshot(),selection);
        OptimizationSceneSettings.requireRoundCountEngine(catalog);
        int wall=selection.path("wallTimeSeconds").asInt(120);if(wall<5||wall>120)throw new IllegalArgumentException("单次计算时限须为 5 至 120 秒");
        var payload=mapper.createObjectNode();payload.set("optimization",request);payload.putObject("limits").put("wallTimeMs",wall*1000).put("memoryMiB",768).put("outputKiB",GcsimGateway.outputBudgetKiB(request));
        String id=UUID.randomUUID().toString();var job=mapper.createObjectNode().put("id",id).put("uid",uid).put("state","QUEUED").put("createdAt",Instant.now().toString())
                .put("workspaceVersion",workspace.path("version").asLong()).put("snapshotId",scan.id()).put("snapshotDigest",scan.snapshot().snapshotDigest()).put("engineRevision",catalog.path("engineRevision").asText()).put("adapterVersion",catalog.path("adapterVersion").asText());
        job.set("selection",selection.deepCopy());job.set("request",request);job.set("mainStatSource",stats.provenance().get("revision"));
        job.put("kind","equipment");
        return enqueue(uid,job,payload,wall,"--optimize");
    }
    synchronized ObjectNode enqueue(String uid,ObjectNode metadata,ObjectNode payload,int wall,String mode){
        OptimizationWorkspace.requireUid(uid);
        if(active.size()>=3)throw new IllegalStateException("计算队列已满");
        if(!Set.of("--optimize","--rotation").contains(mode)||wall<5||wall>120)throw new IllegalArgumentException("无效的计算类型或时限");
        var job=metadata.deepCopy();String id=UUID.randomUUID().toString();
        job.put("id",id).put("uid",uid).put("state","QUEUED").put("createdAt",Instant.now().toString());
        persist(uid,id,job);
        FutureTask<Void> future=new FutureTask<>(()->{execute(uid,id,payload,wall,mode);return null;});active.put(id,future);
        try {executor.execute(future);}catch(RejectedExecutionException error){active.remove(id);job.put("state","FAILED").put("error","计算队列已关闭");persist(uid,id,job);throw error;}
        return publicView(job);
    }
    private void execute(String uid,String id,ObjectNode payload,int wall,String mode) {
        try {
            synchronized(this){var j=stored(uid,id);if(j.path("state").asText().equals("CANCELLED"))return;j.put("state","RUNNING").put("startedAt",Instant.now().toString());persist(uid,id,j);}
            JsonNode response=gateway.execute(mode,payload,Duration.ofSeconds(wall+5L));
            if(!response.path("status").asText().equals("completed")||!response.path("result").isObject())throw new IllegalStateException("计算程序返回了不完整的优化结果");
            synchronized(this){var j=stored(uid,id);if(!j.path("state").asText().equals("CANCELLED")){verifyEngine(j,response.path("result"));j.put("state","COMPLETED").put("finishedAt",Instant.now().toString());j.set("result",response.path("result"));persist(uid,id,j);}}
        } catch(Exception error){
            synchronized(this){var j=stored(uid,id);if(!j.path("state").asText().equals("CANCELLED")){j.put("state",error instanceof InterruptedException?"CANCELLED":"FAILED").put("error",Objects.toString(error.getMessage(),error.getClass().getSimpleName())).put("finishedAt",Instant.now().toString());persist(uid,id,j);}}
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
        } finally{synchronized(this){active.remove(id);}}
    }
    public synchronized ObjectNode get(String uid,String id){
        var job=stored(uid,id);if(Set.of("RUNNING","QUEUED").contains(job.path("state").asText())&&!active.containsKey(id))job.put("state","INTERRUPTED").put("error","服务重启后任务已中断，请重新计算");return publicView(job);
    }
    public synchronized ObjectNode cancel(String uid,String id){
        var job=stored(uid,id);if(Set.of("QUEUED","RUNNING").contains(job.path("state").asText())){
            job.put("state","CANCELLED").put("finishedAt",Instant.now().toString());persist(uid,id,job);
            var future=active.remove(id);if(future!=null){future.cancel(true);executor.remove(future);}
        }return publicView(job);
    }
    public synchronized List<ObjectNode> list(String uid){
        OptimizationWorkspace.requireUid(uid);
        return store.listByKeyPrefixLimited(TYPE+"-summary",uid+":",ObjectNode.class,30).stream().map(ObjectNode::deepCopy).toList();
    }
    private void persist(String uid,String id,ObjectNode job){
        store.put(TYPE,uid+":"+id,job);
        var summary=mapper.createObjectNode();
        for(String field:List.of("id","uid","kind","buildId","state","createdAt","startedAt","finishedAt","snapshotId","workspaceVersion","error"))
            if(job.has(field))summary.set(field,job.get(field).deepCopy());
        store.put(TYPE+"-summary",uid+":"+id,summary);
    }
    synchronized ObjectNode frozen(String uid,String id){return stored(uid,id);}
    private static void verifyEngine(JsonNode job,JsonNode result){
        var reports=new ArrayList<JsonNode>();if(result.path("report").isObject())reports.add(result.path("report"));result.path("plan").path("reports").forEach(reports::add);
        for(JsonNode report:reports)if(!job.path("engineRevision").asText().equals(report.path("engineRevision").asText()))throw new IllegalStateException("排队期间引擎版本发生变化，请重新计算");
        for(JsonNode report:reports)if(job.hasNonNull("adapterVersion")&&!job.path("adapterVersion").asText().isBlank()&&!job.path("adapterVersion").asText().equals(report.path("adapterVersion").asText()))throw new IllegalStateException("排队期间计算适配器发生变化，请重新计算");
    }
    private ObjectNode stored(String uid,String id){OptimizationWorkspace.requireUid(uid);if(!OptimizationWorkspace.key(id))throw new IllegalArgumentException("任务标识无效");return store.get(TYPE,uid+":"+id,ObjectNode.class).map(ObjectNode::deepCopy).orElseThrow(()->new IllegalArgumentException("计算任务不存在"));}
    private static ObjectNode publicView(ObjectNode job){var copy=job.deepCopy();copy.remove("request");return copy;}
    @PreDestroy public synchronized void close(){active.values().forEach(f->f.cancel(true));executor.shutdownNow();}
}
