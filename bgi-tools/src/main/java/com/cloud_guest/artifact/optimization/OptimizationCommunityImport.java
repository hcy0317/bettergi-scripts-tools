package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

public final class OptimizationCommunityImport {
    private final ObjectMapper mapper;
    public OptimizationCommunityImport(ObjectMapper mapper){this.mapper=mapper;}
    public ObjectNode parse(String source){
        var result=mapper.createObjectNode().put("version",1).put("originalScript",source);
        var settings=result.putObject("settings");ArrayNode omitted=result.putArray("omittedDeclarations"),unsupported=result.putArray("unsupported"),warnings=result.putArray("warnings"),characters=result.putArray("characters");
        var rotation=new StringBuilder();var prelude=new StringBuilder();boolean leading=true;var roleKeys=new LinkedHashSet<String>();
        try{for(var statement:OptimizationScriptParts.split(source)){
            String code=statement.code();
            try{
                var role=Pattern.compile("(?s)^([a-zA-Z][a-zA-Z0-9_]*)\\s+(char|add)\\b.*").matcher(code);
                if(role.matches()){roleKeys.add(role.group(1).toLowerCase(Locale.ROOT));omitted.addObject().put("line",statement.line()).put("source",statement.raw());continue;}
                if(code.startsWith("options ")){
                    for(var entry:parameters(code.substring(8)).entrySet()){String key=entry.getKey(),value=entry.getValue();switch(key){
                        case "duration"->settings.put("duration",numeric(value));
                        case "swap_delay"->settings.put("swapDelay",whole(value));
                        case "iteration"->{int count=whole(value);if(count<2||count>1000)throw new IllegalArgumentException("本工具独立验证采样范围为2至1000，请调整参考采样次数");result.put("validationSamples",count);}
                        case "hitlag","defhalt"->{if(!Set.of("true","false").contains(value))throw new IllegalArgumentException("模拟开关需要true或false");settings.put(key,Boolean.parseBoolean(value));}
                        case "workers","debug"->warnings.add("并发和调试设置未从社区脚本接管，由本工具的资源上限统一控制");
                        default->throw new IllegalArgumentException("尚未提供对应设置："+key);
                    }}continue;
                }
                if(code.startsWith("target ")){
                    var values=parameters(code.substring(7));for(String key:values.keySet())if(!Set.of("lvl","resist","radius","pos","hp").contains(key))throw new IllegalArgumentException("尚未提供敌人设置："+key);
                    var targets=settings.withArray("targets");var target=targets.addObject().put("level",whole(values.getOrDefault("lvl","100"))).put("resistance",numeric(values.getOrDefault("resist","0.1"))).put("radius",numeric(values.getOrDefault("radius","1")));
                    String[] position=values.getOrDefault("pos","0,0").split(",",-1);if(position.length!=2)throw new IllegalArgumentException("敌人位置应是两个坐标");target.put("x",numeric(position[0])).put("y",numeric(position[1]));
                    if(values.containsKey("hp")){target.put("hp",numeric(values.get("hp")));settings.put("stopMode","target_or_script");}else target.putNull("hp");continue;
                }
                if(code.startsWith("energy ")){
                    var schedule=Pattern.compile("(?s)^energy\\s+(once|every)\\s+(.+)$").matcher(code);if(!schedule.matches())throw new IllegalArgumentException("不支持该掉球声明");
                    var values=parameters(schedule.group(2));if(!values.keySet().equals(Set.of("interval","amount")))throw new IllegalArgumentException("掉球需明确interval和amount");
                    String[] interval=values.get("interval").split(",",-1);boolean every=schedule.group(1).equals("every");if(interval.length!=(every?2:1))throw new IllegalArgumentException("掉球间隔数量与模式不匹配");
                    var energy=settings.putObject("energy").put("enabled",true).put("mode",schedule.group(1)).put("start",whole(interval[0])).put("amount",whole(values.get("amount")));if(every)energy.put("end",whole(interval[1]));continue;
                }
                if(OptimizationScriptParts.isDeclaration(code))throw new IllegalArgumentException("暂未提供该前置设置的等价表单");
                if(leading&&(code.startsWith("let ")||code.startsWith("fn "))){prelude.append(statement.raw()).append('\n');continue;}
                leading=false;rotation.append(statement.raw()).append('\n');
            }catch(IllegalArgumentException error){unsupported.addObject().put("line",statement.line()).put("source",statement.raw()).put("message",error.getMessage());}
        }}catch(IllegalArgumentException error){unsupported.addObject().put("line",0).put("message",error.getMessage());}
        if(settings.has("targets")&&!settings.has("stopMode"))settings.put("stopMode","fixed_duration");
        if(settings.has("duration")&&!settings.has("stopMode"))settings.put("stopMode","fixed_duration");
        if(!settings.isEmpty()){try{var check=settings.deepCopy().put("id","import-preview");OptimizationSceneSettings.compile(mapper,check);}catch(IllegalArgumentException error){unsupported.addObject().put("line",0).put("message",error.getMessage());}}
        for(String key:roleKeys)characters.add(key);
        if(!prelude.isEmpty())warnings.add("辅助函数和开头变量已从主体循环移出，但默认保留启用；删除拾晶等逻辑会改变伤害计算");
        if(rotation.toString().isBlank())unsupported.addObject().put("line",0).put("message","未找到可用的主体循环或动作");
        return result.put("rotation",rotation.toString().strip()).put("scriptPrelude",prelude.toString().strip()).put("importable",unsupported.isEmpty())
            .put("note","只应用你确认的循环、辅助逻辑和场景设置，不覆盖角色等级、武器、命座、天赋、背包或装备保护。社区参考伤害不是本账号结果。");
    }
    private static Map<String,String> parameters(String text){
        text=text.strip();if(text.endsWith(";"))text=text.substring(0,text.length()-1);
        var result=new LinkedHashMap<String,String>();var matcher=Pattern.compile("([a-zA-Z_]+)\\s*=\\s*([^\\s;]+)").matcher(text);int end=0;
        while(matcher.find()){if(!text.substring(end,matcher.start()).isBlank())throw new IllegalArgumentException("设置表达式暂不能转换，请保留原文手工处理");if(result.put(matcher.group(1),matcher.group(2))!=null)throw new IllegalArgumentException("同一声明重复设置了参数");end=matcher.end();}
        if(!text.substring(end).isBlank()||result.isEmpty())throw new IllegalArgumentException("设置格式无法识别");return result;
    }
    private static double numeric(String text){try{double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(Exception error){throw new IllegalArgumentException("需要明确的有限数值，不能把表达式猜成常量");}}
    private static int whole(String text){double value=numeric(text);if(value!=Math.rint(value)||value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("需要整数参数");return (int)value;}
}
