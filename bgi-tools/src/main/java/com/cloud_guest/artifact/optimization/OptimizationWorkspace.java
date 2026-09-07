package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class OptimizationWorkspace {
    private static final String TYPE="artifact-optimizer-workspace";
    private final ArtifactJsonStore store;
    private final ObjectMapper mapper;
    public OptimizationWorkspace(ArtifactJsonStore store,ObjectMapper mapper) { this.store=store;this.mapper=mapper; }
    public ObjectNode get(String uid) {
        requireUid(uid);
        return store.get(TYPE,uid,ObjectNode.class).map(ObjectNode::deepCopy).orElseGet(()->{
            var node=mapper.createObjectNode().put("version",0);node.putArray("characters");node.putArray("builds");return node;
        });
    }
    public synchronized ObjectNode save(String uid,ObjectNode incoming) {
        var current=get(uid);
        if (!incoming.path("version").canConvertToLong() || incoming.path("version").asLong()!=current.path("version").asLong()) {
            throw new IllegalStateException("配装档案已改变，请重新载入后合并编辑");
        }
        if (!incoming.path("characters").isArray() || !incoming.path("builds").isArray()
                || incoming.path("characters").size()>200 || incoming.path("builds").size()>300
                || incoming.toString().length()>2_000_000) throw new IllegalArgumentException("配装档案格式或大小无效");
        var keys=new java.util.HashSet<String>();
        incoming.path("characters").forEach(c->{if(!key(c.path("key").asText()) || !keys.add(c.path("key").asText())) throw new IllegalArgumentException("角色键无效或重复");});
        keys.clear();
        incoming.path("builds").forEach(b->{if(!key(b.path("id").asText()) || !keys.add(b.path("id").asText())) throw new IllegalArgumentException("Build 标识无效或重复");});
        var saved=incoming.deepCopy().put("version",current.path("version").asLong()+1).put("updatedAt",Instant.now().toString());
        if(current.path("version").asLong()>0) store.put(TYPE+"-previous",uid,current);
        store.put(TYPE,uid,saved);
        return saved.deepCopy();
    }
    public static boolean key(String key) {return key!=null&&key.matches("[a-zA-Z0-9_-]{1,100}");}
    public static void requireUid(String uid) {if(uid==null||!uid.matches("[0-9]{6,12}"))throw new IllegalArgumentException("UID 无效");}
}
