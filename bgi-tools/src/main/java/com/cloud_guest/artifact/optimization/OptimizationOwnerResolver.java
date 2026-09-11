package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Joins OCR names to engine keys via upstream game IDs, never via UI display labels. */
public class OptimizationOwnerResolver {
    private final Map<String,Set<String>> aliases=new HashMap<>();
    private final Map<String,String> physicalIds=new HashMap<>(),representatives=new HashMap<>();
    public OptimizationOwnerResolver(JsonNode catalog,JsonNode workspace,Set<String> activeKeys){
        var inventoryCharacters=new ArrayList<JsonNode>();
        catalog.path("characters").forEach(inventoryCharacters::add);
        catalog.path("inventoryCharacters").forEach(inventoryCharacters::add);
        for(JsonNode c:inventoryCharacters){
            String key=c.path("key").asText(),id=c.path("id").asText(key);physicalIds.put(key,id);
            representatives.putIfAbsent(id,key);add(key,key);
            for(JsonNode alias:c.path("inventoryAliases"))add(alias.asText(),key);
        }
        var activeIds=new HashMap<String,String>();
        for(String key:activeKeys){String id=physicalIds.getOrDefault(key,key),previous=activeIds.putIfAbsent(id,key);if(previous!=null&&!previous.equals(key))throw new IllegalArgumentException("同一实体角色的多个元素形态暂不能作为不同角色同时分配："+previous+" / "+key);representatives.put(id,key);}
        for(JsonNode p:workspace.path("characters")){String key=p.path("key").asText(),custom=p.path("inventoryName").asText("");if(!custom.isBlank()){if(custom.length()>100||custom.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("游戏中装备显示名无效");add(custom,key);}}
    }
    private void add(String alias,String key){aliases.computeIfAbsent(OptimizationCompiler.canonical(alias),ignored->new LinkedHashSet<>()).add(key);}
    public String representative(String key){return representatives.getOrDefault(physicalIds.getOrDefault(key,key),key);}
    public String resolve(String raw){
        if(raw==null||raw.isBlank())return "";
        var matches=aliases.get(OptimizationCompiler.canonical(raw));
        if(matches==null||matches.isEmpty())throw new IllegalArgumentException("无法映射扫描穿戴者“"+raw+"”，请确认 BetterGI 角色目录，或为改名角色填写游戏中装备显示名");
        var ids=new HashSet<String>();matches.forEach(key->ids.add(physicalIds.getOrDefault(key,key)));
        if(ids.size()!=1)throw new IllegalArgumentException("扫描穿戴者名称有歧义："+raw);
        return representative(matches.iterator().next());
    }
}
