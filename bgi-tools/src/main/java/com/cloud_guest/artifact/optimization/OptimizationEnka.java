package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class OptimizationEnka {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Map<String,Cached> cache=new LinkedHashMap<>();
    private record Cached(long expires,JsonNode value){}
    @org.springframework.beans.factory.annotation.Autowired
    public OptimizationEnka(ObjectMapper mapper){this(mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());}
    OptimizationEnka(ObjectMapper mapper,HttpClient client){this.mapper=mapper;this.client=client;}
    public synchronized ObjectNode preview(String uid,JsonNode catalog) throws Exception {
        OptimizationWorkspace.requireUid(uid);
        var cached=cache.get(uid);JsonNode source;
        if(cached!=null&&cached.expires()>System.currentTimeMillis())source=cached.value();
        else {
            var request=HttpRequest.newBuilder(URI.create("https://enka.network/api/uid/"+uid)).timeout(Duration.ofSeconds(15)).header("User-Agent","BetterGI-ArtifactOptimizer/1.0 (local personal build import)").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()){
                if(response.statusCode()==429)throw new IllegalStateException("Enka 请求频率受限，请稍后重试");
                if(response.statusCode()!=200)throw new IllegalStateException("Enka 返回状态 "+response.statusCode()+"，请检查 UID 和展柜公开状态");
                byte[] data=body.readNBytes(2*1024*1024+1);if(data.length>2*1024*1024)throw new IllegalStateException("Enka 响应超过大小限制");source=mapper.readTree(data);
            }
            if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());
            cache.put(uid,new Cached(System.currentTimeMillis()+Math.max(60,Math.min(3600,source.path("ttl").asInt(300)))*1000L,source));
        }
        return convert(source,catalog);
    }
    public ObjectNode convert(JsonNode source,JsonNode catalog){
        var result=mapper.createObjectNode();var imported=result.putArray("characters");var warnings=result.putArray("warnings");
        for(JsonNode avatar:source.path("avatarInfoList")){
            var matches=new ArrayList<JsonNode>();for(JsonNode c:catalog.path("characters"))if(c.path("id").asInt()==avatar.path("avatarId").asInt())matches.add(c);
            if(matches.size()>1)matches.removeIf(c->c.path("sub_id").asInt()!=avatar.path("skillDepotId").asInt());
            if(matches.size()!=1){warnings.add("角色 "+avatar.path("avatarId").asText()+" 无法唯一匹配当前 gcsim 数据，请手动选择");continue;}
            var data=matches.get(0);String key=data.path("key").asText(),name=OptimizationLocalization.name(catalog,"character_names",key);var c=imported.addObject().put("key",key).put("name",name).put("source","enka");
            c.put("constellation",avatar.path("talentIdList").size());
            copyInt(c,"level",avatar.path("propMap").path("4001").path("val"));
            var asc=avatar.path("propMap").path("1002").path("val");if(!asc.isMissingNode())c.put("maxLevel",maxLevel(asc.asInt()));else c.putNull("maxLevel");
            var talents=c.putArray("talents");for(String skill:List.of("attack","skill","burst")){
                String skillId=data.path("skill_details").path(skill).asText();var value=avatar.path("skillLevelMap").path(skillId);
                if(value.isIntegralNumber())talents.add(value.asInt());else{talents.addNull();warnings.add(name+"的"+Map.of("attack","普通攻击","skill","元素战技","burst","元素爆发").get(skill)+"基础等级未知，请手动补充");}
            }
            boolean weaponFound=false;
            for(JsonNode equipment:avatar.path("equipList"))if(equipment.has("weapon")){
                var weapon=equipment.path("weapon");var weaponKey="";
                for(JsonNode w:catalog.path("weapons"))if(w.path("id").asInt()==equipment.path("itemId").asInt())weaponKey=w.path("key").asText();
                c.put("weapon",weaponKey);copyInt(c,"weaponLevel",weapon.path("level"));c.put("weaponMaxLevel",maxLevel(weapon.path("promoteLevel").asInt(0)));
                var refinements=weapon.path("affixMap").elements();c.put("refinement",refinements.hasNext()?refinements.next().asInt()+1:1);weaponFound=!weaponKey.isBlank();
            }
            if(!weaponFound){c.putNull("weapon");warnings.add(name+"武器未知，请手动补充");}
        }
        if(imported.isEmpty())warnings.add("没有可导入的公开展柜角色。Enka 不提供全账号背包。");
        result.put("note","仅预览个人条件；需逐项确认后导入，不覆盖 TAG、保护、目标和配队 Build。天赋采用基础等级，由 gcsim 应用命座提升。");return result;
    }
    private static void copyInt(ObjectNode target,String key,JsonNode source){if(source.isMissingNode()||source.isNull())target.putNull(key);else target.put(key,source.asInt());}
    private static int maxLevel(int asc){return switch(asc){case 0->20;case 1->40;case 2->50;case 3->60;case 4->70;case 5->80;case 6->90;default->0;};}
}
