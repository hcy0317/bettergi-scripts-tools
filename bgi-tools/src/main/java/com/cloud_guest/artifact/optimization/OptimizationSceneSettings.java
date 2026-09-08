package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Set;

/** One scene contract for legacy saved Builds and the structured scene editor. */
public final class OptimizationSceneSettings {
    private OptimizationSceneSettings(){}
    public static String compile(ObjectMapper mapper,JsonNode build){
        String id=build.path("id").asText(),mode=build.path("stopMode").asText("fixed_duration");
        if(!Set.of("fixed_duration","target_or_script").contains(mode))throw bad(id,"scene","请选择有效的单次模拟停止方式");
        var text=new StringBuilder("options ");
        if(mode.equals("fixed_duration"))text.append("duration=").append(decimal(number(build,"duration",1,600,60.0,id,"scene"))).append(' ');
        text.append("swap_delay=").append(integer(build,"swapDelay",0,120,1,id,"scene"));
        for(String flag:Set.of("hitlag","defhalt")){if(build.has(flag)){if(!build.get(flag).isBoolean())throw bad(id,"scene","模拟选项必须为开关值");text.append(' ').append(flag).append('=').append(build.get(flag).asBoolean());}}
        text.append(";\n");
        JsonNode targets=build.path("targets");
        if(targets.isMissingNode()){
            ArrayNode compatible=mapper.createArrayNode();
            int count=integer(build,"enemyCount",1,10,1,id,"scene");
            for(int i=0;i<count;i++)compatible.addObject().put("level",integer(build,"enemyLevel",1,200,100,id,"scene")).put("resistance",number(build,"resistance",-1,10,0.1,id,"scene")).put("radius",1).put("x",0).put("y",0);
            targets=compatible;
        }
        if(!targets.isArray()||targets.isEmpty()||targets.size()>10)throw bad(id,"scene","请配置1至10个敌人");
        int index=0;for(JsonNode target:targets){String field="targets."+index++;
            text.append("target lvl=").append(integer(target,"level",1,200,null,id,field)).append(" resist=").append(decimal(number(target,"resistance",-1,10,null,id,field)))
                .append(" radius=").append(decimal(number(target,"radius",0.01,100,1.0,id,field))).append(" pos=").append(decimal(number(target,"x",-1000,1000,0.0,id,field))).append(',').append(decimal(number(target,"y",-1000,1000,0.0,id,field)));
            if(mode.equals("target_or_script"))text.append(" hp=").append(decimal(number(target,"hp",1,1e12,null,id,field)));
            text.append(";\n");
        }
        JsonNode energy=build.path("energy");
        if(energy.path("enabled").asBoolean()){
            String schedule=energy.path("mode").asText();if(!Set.of("once","every").contains(schedule))throw bad(id,"energy","请选择一次或周期掉球");
            int start=integer(energy,"start",1,36000,null,id,"energy"),amount=integer(energy,"amount",1,100,null,id,"energy");
            text.append("energy ").append(schedule).append(" interval=").append(start);
            if(schedule.equals("every")){int end=integer(energy,"end",1,36000,null,id,"energy");if(end<=start)throw bad(id,"energy","周期最大掉球间隔必须大于最小间隔");text.append(',').append(end);}
            text.append(" amount=").append(amount).append(";\n");
        }
        return text.toString();
    }
    public static int integer(JsonNode node,String key,int min,int max,Integer fallback,String id,String field){JsonNode v=node.path(key);if(v.isMissingNode()&&fallback!=null)return fallback;if(!v.isIntegralNumber()||!v.canConvertToInt()||v.asInt()<min||v.asInt()>max)throw bad(id,field,"字段“"+label(key)+"”未填写或超出范围");return v.asInt();}
    private static double number(JsonNode node,String key,double min,double max,Double fallback,String id,String field){JsonNode v=node.path(key);if(v.isMissingNode()&&fallback!=null)return fallback;if(!v.isNumber()||!Double.isFinite(v.asDouble())||v.asDouble()<min||v.asDouble()>max)throw bad(id,field,"字段“"+label(key)+"”未填写或超出范围");return v.asDouble();}
    private static String decimal(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static String label(String key){return switch(key){case "duration"->"单次模拟时长";case "hp"->"敌人血量";case "radius"->"敌人半径";case "resistance"->"敌人抗性";case "level","enemyLevel"->"敌人等级";case "start"->"最小间隔/掉落时间";case "end"->"最大间隔";case "amount"->"微粒数量";case "swapDelay"->"切人延迟";case "enemyCount"->"敌人数";default->key;};}
    private static OptimizationValidationException bad(String id,String field,String message){return OptimizationValidationException.scene(id,field,message);}
}
