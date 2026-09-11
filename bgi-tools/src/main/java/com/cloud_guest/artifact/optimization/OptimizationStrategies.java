package com.cloud_guest.artifact.optimization;

import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.*;

@Service
public class OptimizationStrategies {
    private final CultivationMaterialSourceCatalog sources;
    private final ObjectMapper mapper;
    public record Mapping(Map<String,String> aliases,Map<String,String> names){}
    public OptimizationStrategies(CultivationMaterialSourceCatalog sources,ObjectMapper mapper){this.sources=sources;this.mapper=mapper;}
    public List<String> list() throws Exception {
        Path directory=directory();if(!Files.isDirectory(directory))return List.of();
        try(var stream=Files.list(directory)){return stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).map(p->p.getFileName().toString()).filter(n->n.endsWith(".txt")||n.endsWith(".json")).sorted().limit(300).toList();}
    }
    public ObjectNode read(String name,Mapping mapping) throws Exception {
        Path file=file(name);if(Files.size(file)>100_000)throw new IllegalArgumentException("策略文件超过 100 KB");String text=Files.readString(file);
        if(name.endsWith(".json")){
            JsonNode json=mapper.readTree(text);
            if(!json.path("actions").isArray()||!json.path("info").path("declarations").isMissingNode())return unsupported(text,"JSON 声明/控制流尚未进入有限动作转换范围，请保留原文作为参考");
            var lines=new ArrayList<String>();for(JsonNode a:json.path("actions")){if(a.size()!=2||!a.has("character")||!a.has("action"))return unsupported(text,"JSON 动作含额外条件或控制字段，不能静默丢弃");lines.add(a.path("character").asText()+" "+a.path("action").asText());}text=String.join("\n",lines);
        }
        var result=new OptimizationRotationCompiler(mapper).parse(text,mapping.aliases());result.put("sourceName",name).put("originalScript",text);return result;
    }
    private ObjectNode unsupported(String text,String reason){var value=mapper.createObjectNode().put("supported",false).put("originalScript",text);value.putArray("actions");value.putArray("issues").add(reason);return value;}
    public Mapping mapping(JsonNode engineCatalog,Set<String> members) throws Exception {
        Path data=sources.betterGiRoot().resolve(Path.of("GameTask","AutoFight","Assets","combat_avatar.json"));
        if(!Files.isRegularFile(data)||Files.size(data)>2_000_000)throw new IllegalStateException("BetterGI 角色别名目录不可用，仅可进行模拟参考");
        JsonNode nativeCatalog=mapper.readTree(data.toFile());var aliases=new LinkedHashMap<String,String>();var names=new LinkedHashMap<String,String>();
        for(JsonNode c:engineCatalog.path("characters"))if(members.contains(c.path("key").asText())){
            String key=c.path("key").asText();for(JsonNode n:nativeCatalog)if(n.path("id").asLong(-1)==c.path("id").asLong(-2)){
                names.put(key,n.path("name").asText());aliases.put(key,key);
                for(JsonNode alias:n.path("alias")){String previous=aliases.putIfAbsent(alias.asText(),key);if(previous!=null&&!previous.equals(key))throw new IllegalArgumentException("同一队伍的角色别名有歧义，请确认旅行者元素和队伍角色");}
            }
        }
        return new Mapping(Map.copyOf(aliases),Map.copyOf(names));
    }
    private Path directory() throws Exception {
        Path root=sources.betterGiRoot().toRealPath();Path directory=root.resolve(Path.of("User","AutoFight"));
        if(Files.exists(directory)&&!directory.toRealPath().startsWith(root))throw new IllegalStateException("战斗策略目录离开了已配置 BetterGI 根");return directory;
    }
    private Path file(String name) throws Exception {
        if(name==null||name.contains("/")||name.contains("\\")||name.contains("\n")||name.contains("\r")||(!name.endsWith(".txt")&&!name.endsWith(".json")))throw new IllegalArgumentException("策略文件名无效");
        Path directory=directory(),file=directory.resolve(name).normalize();if(!file.getParent().equals(directory)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("策略文件不存在或不可读取");return file;
    }
}
