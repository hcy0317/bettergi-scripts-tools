package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Revalidates engine edits against the frozen input; never trusts an arbitrary returned script. */
final class OptimizationNativeStructure {
    private final ObjectMapper mapper;
    private final String source;
    private final Map<String,String> aliases,names;
    private final ObjectNode program;
    OptimizationNativeStructure(ObjectMapper mapper,String source,Map<String,String> aliases,Map<String,String> names) {
        this.mapper=mapper;this.source=source;this.aliases=aliases;this.names=names;
        program=new OptimizationNativeFlow(mapper,source,aliases).program();
    }
    String apply(JsonNode edits) {
        if(!edits.isArray()||edits.size()>512)throw new IllegalArgumentException("结构变更清单无效");
        if(edits.isEmpty())return source;
        for(JsonNode edit:edits) {
            String block=edit.path("block").asText(),id=edit.path("node").asText();
            JsonNode data=block.equals("$root")?program:program.path("blocks").path(block);
            if(data.has("macro"))throw new IllegalArgumentException("不能重排或删除输入宏");
            JsonNode sequence=block.equals("$root")?data.path("root"):data.path("nodes");
            if(!sequence.isArray())throw new IllegalArgumentException("未知结构块："+block);
            var nodes=(ArrayNode)sequence;int index=find(nodes,id);var node=nodes.get(index);
            switch(edit.path("kind").asText()) {
                case "swap" -> {
                    if(index+1>=nodes.size()||!nodes.get(index+1).path("id").asText().equals(edit.path("other").asText())||!canSwap(node,nodes.get(index+1)))throw new IllegalArgumentException("结构重排违反相邻节点或记录依赖");
                    var next=nodes.get(index+1);nodes.set(index,next);nodes.set(index+1,node);
                }
                case "drop" -> {
                    String kind=node.path("kind").asText();var options=node.path("options");
                    boolean allowed=kind.equals("check")&&index>0&&nodes.get(index-1).path("kind").asText().equals("check");
                    if(Set.of("skill","burst").contains(kind)&&!options.path("required").asText().equals("true")) {
                        allowed=true;var keys=options.fieldNames();while(keys.hasNext())if(!Set.of("if","fast").contains(keys.next()))allowed=false;
                    }
                    if(!allowed||nodes.size()==1)throw new IllegalArgumentException("不能删除必要动作、守卫或记录生产者");
                    nodes.remove(index);
                }
                case "collapse_branch" -> {
                    var options=node.path("options");String target=options.path("then").asText();
                    if(!node.path("kind").asText().equals("branch")||target.isBlank()||!target.equals(options.path("else").asText())||!target.equals(options.path("unknown").asText()))throw new IllegalArgumentException("分支目标不等价，不能删除条件");
                    var changed=((ObjectNode)node).deepCopy();changed.put("kind","call");changed.remove("condition");changed.putArray("args").add(target);var remaining=changed.putObject("options");if(options.path("required").asText().equals("true"))remaining.put("required","true");nodes.set(index,changed);
                }
                default -> throw new IllegalArgumentException("未知结构操作");
            }
        }
        var out=new StringBuilder("// gcsim结构优化候选；原文件未覆盖，模拟结果不代表实机等价\n");
        if(program.path("loop").asBoolean())out.append("strategy(loop=battle)\n");
        program.path("timings").fields().forEachRemaining(e->{out.append("timing(").append(e.getKey());e.getValue().fields().forEachRemaining(o->out.append(',').append(o.getKey()).append('=').append(o.getValue().asText()));out.append(")\n");});
        for(JsonNode n:program.path("root"))out.append(render(n)).append('\n');
        for(JsonNode b:program.path("blocks")) {
            JsonNode declaration=b.path("declaration");out.append('\n');
            if(b.has("macro")){out.append(source,declaration.path("start").asInt(),b.path("end").asInt()).append('\n');continue;}
            out.append(raw(declaration)).append(" {\n");for(JsonNode n:b.path("nodes"))out.append("    ").append(render(n)).append('\n');out.append("}\n");
        }
        String candidate=out.toString();new OptimizationNativeFlow(mapper,candidate,aliases).program();return candidate;
    }
    private String raw(JsonNode node){int a=node.path("start").asInt(-1),b=node.path("end").asInt(-1);if(a<0||b<a||b>source.length())throw new IllegalArgumentException("节点源码位置无效");return source.substring(a,b).strip();}
    private String render(JsonNode node) {
        String text=raw(node),kind=node.path("kind").asText();
        int space=text.indexOf(' '),open=text.indexOf('(');if(space>0&&(open<0||space<open)&&aliases.containsKey(text.substring(0,space)))text=text.substring(space+1).stripLeading();
        if(kind.equals("call")&&text.startsWith("branch"))text="call("+node.path("args").get(0).asText()+(node.path("options").path("required").asText().equals("true")?",required":"")+")";
        String actor=node.path("character").asText(),name=names.getOrDefault(actor,actor);
        if(actor.isBlank()&&Set.of("call","branch").contains(kind))return text;
        if(!actor.equals(aliases.get(name)))throw new IllegalArgumentException("导出角色名不属于原队伍："+actor);
        return name+" "+text;
    }
    private static int find(ArrayNode nodes,String id){for(int i=0;i<nodes.size();i++)if(nodes.get(i).path("id").asText().equals(id))return i;throw new IllegalArgumentException("结构节点不属于原文："+id);}
    private boolean canSwap(JsonNode a,JsonNode b){
        if(a.path("options").has("once")||b.path("options").has("once"))return false;
        var ar=new HashSet<String>();var aw=new HashSet<String>();var br=new HashSet<String>();var bw=new HashSet<String>();effects(a,ar,aw,new HashSet<>());effects(b,br,bw,new HashSet<>());
        return Collections.disjoint(aw,br)&&Collections.disjoint(aw,bw)&&Collections.disjoint(bw,ar);
    }
    private void effects(JsonNode n,Set<String> reads,Set<String> writes,Set<String> visited){
        var o=n.path("options");for(String key:List.of("keep","maintain","watch","refresh"))if(o.has(key))reads.add(o.path(key).asText());for(String key:List.of("record","refresh"))if(o.has(key))writes.add(o.path(key).asText());
        condition(n.path("condition"),reads);condition(n.path("requires"),reads);var targets=new HashSet<String>();if(n.path("kind").asText().equals("call"))targets.add(n.path("args").path(0).asText());for(String k:List.of("then","else","unknown","onfail","watch-target"))if(o.has(k))targets.add(o.path(k).asText());
        for(String name:targets)if(visited.add(name)){var b=program.path("blocks").path(name);effects(b.path("declaration"),reads,writes,visited);for(JsonNode child:b.path("nodes"))effects(child,reads,writes,visited);}
    }
    private static void condition(JsonNode n,Set<String> reads){if(n.isMissingNode())return;if(n.path("name").asText().startsWith("record-"))reads.add(n.path("argument").asText());condition(n.path("left"),reads);condition(n.path("right"),reads);}
}
