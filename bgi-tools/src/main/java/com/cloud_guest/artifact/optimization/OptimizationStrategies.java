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
        try(var stream=Files.walk(directory,5)){return stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).filter(p->{try{return p.toRealPath().startsWith(directory.toRealPath());}catch(java.io.IOException e){return false;}}).map(p->directory.relativize(p).toString().replace('\\','/')).filter(n->n.endsWith(".txt")||n.endsWith(".json")).sorted().limit(300).toList();}
    }
    /** Authenticated read-only compatibility API, independent of account Build state. */
    public ObjectNode source(String name) throws Exception {
        Path file=file(name);if(Files.size(file)>100_000)throw new IllegalArgumentException("策略文件超过 100 KB");
        String text=Files.readString(file);if(text.startsWith("\uFEFF"))text=text.substring(1);
        var result=mapper.createObjectNode().put("version",1).put("readable",true).put("sourceName",name).put("format",name.endsWith(".json")?"json":"text").put("originalScript",text);
        var sections=result.putArray("sections");sections.addObject().put("id","whole").put("name","整份策略（保留全部控制流）").put("source",text);
        if(!name.endsWith(".json")){
            String[] lines=text.split("\\R",-1);
            var header=java.util.regex.Pattern.compile("^\\s*segment\\(([^,()]+)(?:,(.*))?\\)\\s*\\{\\s*(?://.*)?$");
            for(int i=0;i<lines.length;i++){
                var match=header.matcher(lines[i]);if(!match.matches())continue;
                int end=i+1;while(end<lines.length&&!lines[end].trim().matches("}\\s*(?://.*)?"))end++;
                if(end==lines.length)continue;
                // Keep the header, including guards, in each selectable section.
                // The converter, not this read API, judges semantic support.
                sections.addObject().put("id","line-"+(i+1)).put("name",match.group(1).trim()).put("startLine",i+1)
                    .put("source",String.join("\n",Arrays.copyOfRange(lines,i,end+1)));
                i=end;
            }
        }
        result.put("note","已读取原文。可选择整份策略或一个明确片段进行兼容性检查；分支、状态依赖和输入宏不会被静默删除，读取不代表可直接模拟或执行。");return result;
    }
    public ObjectNode read(String name,Mapping mapping) throws Exception {
        return parseSource(source(name).path("originalScript").asText(),mapping).put("sourceName",name);
    }
    public ObjectNode parseSource(String original,Mapping mapping)throws Exception {
        if(original==null||original.length()>100_000)throw new IllegalArgumentException("策略为空或超过大小限制");
        String text=original.stripLeading();if(text.startsWith("\uFEFF"))text=text.substring(1);
        if(text.startsWith("{")){
            JsonNode json=mapper.readTree(text);
            if(!json.path("actions").isArray()||!json.path("info").path("declarations").isMissingNode())return unsupported(text,"JSON 声明/控制流尚未进入有限动作转换范围，请保留原文作为参考");
            var fields=json.fieldNames();while(fields.hasNext())if(!Set.of("actions","info").contains(fields.next()))return unsupported(text,"策略包含额外控制字段，保留原文，不自动转换");
            if(json.has("info")){var infoFields=json.path("info").fieldNames();while(infoFields.hasNext())if(!Set.of("name","author","description").contains(infoFields.next()))return unsupported(text,"策略信息包含未支持的声明，不能静默丢弃");}
            var lines=new ArrayList<String>();for(JsonNode a:json.path("actions")){if(a.size()!=2||!a.has("character")||!a.has("action"))return unsupported(text,"JSON 动作含额外条件或控制字段，不能静默丢弃");lines.add(a.path("character").asText()+" "+a.path("action").asText());}text=String.join("\n",lines);
        }
        var result=new OptimizationRotationCompiler(mapper).parse(text,mapping.aliases());result.put("originalScript",original);return result;
    }
    private ObjectNode unsupported(String text,String reason){var value=mapper.createObjectNode().put("supported",false).put("originalScript",text);value.putArray("actions");value.putArray("issues").add(reason);return value;}
    public Mapping mapping(JsonNode engineCatalog,Set<String> members) throws Exception {
        Path data=sources.betterGiRoot().resolve(Path.of("GameTask","AutoFight","Assets","combat_avatar.json"));
        JsonNode nativeCatalog=Files.isRegularFile(data)&&Files.size(data)<=2_000_000?mapper.readTree(data.toFile()):mapper.createArrayNode();
        var aliases=new LinkedHashMap<>(OptimizationLocalization.aliases(engineCatalog,members));var names=new LinkedHashMap<String,String>();
        for(JsonNode c:engineCatalog.path("characters"))if(members.contains(c.path("key").asText())){
            String key=c.path("key").asText();for(JsonNode n:nativeCatalog)if(n.path("id").asLong(-1)==c.path("id").asLong(-2)){
                names.put(key,n.path("name").asText());aliases.put(key,key);aliases.merge(n.path("name").asText(),key,(a,b)->a.equals(b)?a:"");
                for(JsonNode alias:n.path("alias"))aliases.merge(alias.asText(),key,(a,b)->a.equals(b)?a:"");
            }
        }
        return new Mapping(Map.copyOf(aliases),Map.copyOf(names));
    }
    private Path directory() throws Exception {
        Path root=sources.betterGiRoot().toRealPath();Path directory=root.resolve(Path.of("User","AutoFight"));
        if(Files.exists(directory)&&!directory.toRealPath().startsWith(root))throw new IllegalStateException("战斗策略目录离开了已配置 BetterGI 根");return directory;
    }
    private Path file(String name) throws Exception {
        if(name==null||name.length()>800||name.contains(":")||name.contains("\n")||name.contains("\r")||(!name.endsWith(".txt")&&!name.endsWith(".json")))throw new IllegalArgumentException("策略文件名无效");
        String normalized=name.replace('\\','/');
        if(Arrays.stream(normalized.split("/",-1)).anyMatch(part->part.isBlank()||part.equals(".")||part.equals("..")))throw new IllegalArgumentException("策略路径不能越界");
        Path directory=directory(),file=directory.resolve(normalized).normalize();if(!file.startsWith(directory)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.toRealPath().startsWith(directory.toRealPath()))throw new IllegalArgumentException("策略文件不存在或不可读取");return file;
    }
}
