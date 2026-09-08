package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class OptimizationMainStats implements OptimizationCompiler.MainStats {
    private final JsonNode data;
    public OptimizationMainStats(ObjectMapper mapper) {
        try(var stream=new ClassPathResource("artifact/optimizer-main-stats.json").getInputStream()){data=mapper.readTree(stream);}
        catch(Exception error){throw new IllegalStateException("无法加载版本化主词条数据",error);}
    }
    @Override public double value(int rarity,int level,String key){
        var values=data.path("main").path(Integer.toString(rarity)).path(key);
        if(!values.isArray()||level<0||level>=values.size()||!values.get(level).isNumber())throw new IllegalArgumentException("不支持的主词条/星级/等级："+key+"/"+rarity+"/"+level);
        double value=values.get(level).asDouble();if(key.endsWith("_"))value*=100;
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("主词条数值无效");return value;
    }
    public JsonNode provenance(){return data.deepCopy();}
}
