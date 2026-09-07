package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;

@Service
public class OptimizationCommunityService {
    private static final String ORIGIN="https://gcsim.app";
    private static final Set<Integer> TAGS=Set.of(0,1,2,5,6,7,8,9);
    private final ObjectMapper mapper;private final HttpClient client;private final Semaphore slots=new Semaphore(3);
    private record Cached(long expires,JsonNode value){}
    private final Map<String,Cached> cache=new LinkedHashMap<>();
    @Autowired public OptimizationCommunityService(ObjectMapper mapper){this(mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());}
    OptimizationCommunityService(ObjectMapper mapper,HttpClient client){this.mapper=mapper;this.client=client;}
    public ObjectNode search(JsonNode input)throws Exception {
        var fields=input.fieldNames();while(fields.hasNext())if(!Set.of("include","exclude","tags","excludedTags","sort","direction","page").contains(fields.next()))throw new IllegalArgumentException("搜索仅接受列明的角色、标签、排序和分页参数");
        if(!input.isObject()||input.toString().length()>8192)throw new IllegalArgumentException("搜索参数格式或大小无效");
        int page=input.path("page").asInt(1);if(input.has("page")&&!input.path("page").isIntegralNumber()||page<1||page>1000)throw new IllegalArgumentException("搜索页码无效");
        var query=mapper.createObjectNode();var condition=query.putObject("query");var and=mapper.createArrayNode();
        for(String field:List.of("include","exclude")){var values=input.path(field);if(!values.isMissingNode()&&(!values.isArray()||values.size()>(field.equals("include")?4:16)))throw new IllegalArgumentException("角色筛选数量无效");
            var seen=new HashSet<String>();for(JsonNode value:values){String key=value.asText();if(!key.matches("[a-z][a-z0-9]{0,49}"))throw new IllegalArgumentException("角色筛选键无效");boolean excluded=field.equals("exclude");
                if(key.startsWith("aether")||key.startsWith("lumine")){String element=key.substring(6);if(!seen.add(element))continue;if(excluded){for(String prefix:List.of("aether","lumine"))and.addObject().putObject("summary.char_names").put("$ne",prefix+element);}else{var either=and.addObject().putArray("$or");for(String prefix:List.of("aether","lumine"))either.addObject().put("summary.char_names",prefix+element);}}
                else if(excluded)and.addObject().putObject("summary.char_names").put("$ne",key);else and.addObject().put("summary.char_names",key);
            }
        }
        for(String field:List.of("tags","excludedTags")){JsonNode values=input.path(field);if(values.isMissingNode()&&field.equals("excludedTags"))values=mapper.createArrayNode().add(9);if(!values.isMissingNode()&&!values.isArray())throw new IllegalArgumentException("标签必须是列表");
            if(values.size()>8)throw new IllegalArgumentException("社区标签筛选过多");var tags=mapper.createArrayNode();for(JsonNode value:values){if(!value.isIntegralNumber()||!TAGS.contains(value.asInt()))throw new IllegalArgumentException("未知社区标签");tags.add(value.asInt());}if(!tags.isEmpty())and.addObject().putObject("accepted_tags").set(field.equals("tags")?"$in":"$nin",tags);
        }
        if(!and.isEmpty())condition.set("$and",and);query.put("limit",20).put("skip",(page-1)*20);
        String sort=input.path("sort").asText("date"),direction=input.path("direction").asText("desc");if(!Set.of("date","dps").contains(sort)||!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("排序方式无效");
        query.putObject("sort").put(sort.equals("dps")?"summary.mean_dps_per_target":"create_date",direction.equals("asc")?1:-1);
        JsonNode response=fetch("/api/db?q="+URLEncoder.encode(query.toString(),StandardCharsets.UTF_8));
        if(!response.path("data").isArray())throw new IllegalStateException("社区响应格式变化，未修改任何档案");
        var result=mapper.createObjectNode().put("page",page).put("hasMore",response.path("data").size()==20);var entries=result.putArray("entries");
        for(JsonNode entry:response.path("data")){String id=entry.path("_id").asText();if(!validId(id))continue;var item=entries.addObject().put("id",id).put("description",entry.path("description").asText()).put("author",entry.path("submitter").asText()).put("createdAt",entry.path("create_date").asLong()).put("dps",entry.path("summary").path("mean_dps_per_target").asDouble()).put("url","https://gcsim.app/db/"+id);item.set("characters",entry.path("summary").path("char_names").deepCopy());}
        return result;
    }
    public ObjectNode read(String reference)throws Exception {
        if(reference==null)throw new IllegalArgumentException("请输入社区条目链接或编号");String id=reference.trim();
        if(id.contains("://")){URI uri=URI.create(id);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!Set.of("simpact.app","gcsim.app").contains(uri.getHost().toLowerCase(Locale.ROOT))||uri.getUserInfo()!=null||uri.getPort()!=-1||!uri.getPath().matches("/db/[a-zA-Z0-9_-]{5,64}/?"))throw new IllegalArgumentException("只接受 gcsim 或 sim pact 的数据库条目链接");id=uri.getPath().substring(4).replaceAll("/$","");}
        if(!validId(id))throw new IllegalArgumentException("社区条目编号无效");JsonNode entry=fetch("/api/db/id/"+id);
        if(!entry.path("_id").asText().equals(id)||!entry.path("config").isTextual()||entry.path("config").asText().length()>100_000)throw new IllegalStateException("社区条目身份或脚本格式无效");
        var result=new OptimizationCommunityImport(mapper).parse(entry.path("config").asText());return result.put("sourceUrl","https://gcsim.app/db/"+id).put("description",entry.path("description").asText());
    }
    private static boolean validId(String id){return id.matches("[a-zA-Z0-9_-]{5,64}");}
    private JsonNode fetch(String path)throws Exception {
        synchronized(cache){var cached=cache.get(path);if(cached!=null&&cached.expires>System.currentTimeMillis())return cached.value.deepCopy();}
        if(!slots.tryAcquire())throw new IllegalStateException("社区请求正在处理中，请稍后重试");
        try{
            var request=HttpRequest.newBuilder(URI.create(ORIGIN+path)).timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("User-Agent","BetterGI-ArtifactOptimizer/1.0").GET().build();
            var response=client.send(request,info->new BoundedBody(4*1024*1024));
            if(response.statusCode()!=200)throw new IllegalStateException("社区服务返回 "+response.statusCode()+"，请稍后重试，或直接粘贴脚本");JsonNode value=mapper.readTree(response.body());
            synchronized(cache){if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(path,new Cached(System.currentTimeMillis()+300_000,value));}return value.deepCopy();
        }catch(java.io.IOException error){throw new IllegalStateException("社区连接失败，请稍后重试，或直接粘贴脚本",error);}finally{slots.release();}
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> completed=new CompletableFuture<>();private Flow.Subscription subscription;
        BoundedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return completed;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(Long.MAX_VALUE);}
        public void onNext(List<ByteBuffer> buffers){for(ByteBuffer buffer:buffers){if(bytes.size()+buffer.remaining()>limit){subscription.cancel();completed.completeExceptionally(new IllegalStateException("社区响应超过大小限制"));return;}byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}}
        public void onError(Throwable error){completed.completeExceptionally(error);}
        public void onComplete(){completed.complete(bytes.toByteArray());}
    }
}
