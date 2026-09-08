package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Joins OCR names to engine keys via upstream game IDs, never via UI display labels. */
public class OptimizationOwnerResolver {
    private final Map<String,Set<String>> aliases=new HashMap<>();
    private final Map<String,String> physicalIds=new HashMap<>(),representatives=new HashMap<>(),nativeNames=new HashMap<>(),inventoryNames=new HashMap<>();
    public OptimizationOwnerResolver(JsonNode catalog,JsonNode workspace,Set<String> activeKeys){
        for(String source:List.of("characters","inventoryCharacters"))for(JsonNode c:catalog.path(source)){
            String key=c.path("key").asText(),id=c.path("inventoryId").asText(c.path("id").asText(key));physicalIds.put(key,id);
            representatives.putIfAbsent(id,key);add(key,key);
            if(!c.path("nativeName").asText().isBlank()){nativeNames.put(id,c.path("nativeName").asText());add(c.path("nativeName").asText(),key);}
            for(JsonNode alias:c.path("inventoryAliases"))add(alias.asText(),key);
        }
        var activeIds=new HashMap<String,String>();
        for(String key:activeKeys){String id=physicalIds.getOrDefault(key,key),previous=activeIds.putIfAbsent(id,key);if(previous!=null&&!previous.equals(key))throw new IllegalArgumentException("同一实体角色的多个元素形态暂不能作为不同角色同时分配："+previous+" / "+key);representatives.put(id,key);}
        for(JsonNode p:workspace.path("characters")){String key=p.path("key").asText(),custom=p.path("inventoryName").asText("");if(!custom.isBlank()){
            if(custom.length()>100||custom.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("游戏中装备显示名无效");
            String id=physicalIds.getOrDefault(key,key),previous=inventoryNames.putIfAbsent(id,custom);
            if(previous!=null&&!previous.equals(custom))throw new IllegalArgumentException("同一实体角色填写了不同的游戏中装备显示名");
            add(custom,key);
        }}
    }
    private void add(String alias,String key){aliases.computeIfAbsent(OptimizationCompiler.canonical(alias),ignored->new LinkedHashSet<>()).add(key);}
    public String representative(String key){return representatives.getOrDefault(physicalIds.getOrDefault(key,key),key);}
    public String nativeName(String key){
        String name=nativeNames.get(physicalIds.getOrDefault(key,key));
        if(name==null||name.isBlank())throw new IllegalArgumentException("缺少可核验的游戏角色名称："+key);
        return name;
    }
    public boolean known(String key){return physicalIds.containsKey(key);}
    public String inventoryName(String key){String custom=inventoryNames.get(physicalIds.getOrDefault(key,key));return custom==null?nativeName(key):custom;}
    public Set<String> protectedKeys(JsonNode workspace){
        var protectedKeys=new LinkedHashSet<String>();
        for(JsonNode profile:workspace.path("characters"))if(profile.path("protected").asBoolean()){
            String key=profile.path("key").asText();
            if(!known(key))throw new IllegalArgumentException("原档案中受保护角色的身份无法核实："+key+"；原保护未取消，请重新绑定角色身份");
            protectedKeys.add(representative(key));
        }
        JsonNode extra=workspace.path("protectedInventoryOwners");
        if(!extra.isMissingNode()&&(!extra.isArray()||extra.size()>200))throw new IllegalArgumentException("库存角色保护必须是至多200项的列表");
        for(JsonNode key:extra){
            if(!key.isTextual()||!known(key.asText()))throw new IllegalArgumentException("库存保护角色无法核实："+key.asText());
            protectedKeys.add(representative(key.asText()));
        }
        return protectedKeys;
    }
    public String resolve(String raw){
        if(raw==null||raw.isBlank())return "";
        var matches=aliases.get(OptimizationCompiler.canonical(raw));
        if(matches==null||matches.isEmpty())throw new IllegalArgumentException("无法映射扫描穿戴者“"+raw+"”，请确认 BetterGI 角色目录，或为改名角色填写游戏中装备显示名");
        var ids=new HashSet<String>();matches.forEach(key->ids.add(physicalIds.getOrDefault(key,key)));
        if(ids.size()!=1)throw new IllegalArgumentException("扫描穿戴者名称有歧义："+raw);
        return representative(matches.iterator().next());
    }
}
