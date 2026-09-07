package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.regex.Pattern;

/** Deliberately finite semantic subset. Unknown conditions must never be erased. */
public class OptimizationRotationCompiler {
    private final ObjectMapper mapper;
    public OptimizationRotationCompiler(ObjectMapper mapper){this.mapper=mapper;}
    public ObjectNode parse(String source,Map<String,String> aliases){
        if(source==null||source.length()>100_000)throw new IllegalArgumentException("战斗策略为空或超过大小限制");
        var result=mapper.createObjectNode();var actions=result.putArray("actions");var issues=result.putArray("issues");
        String character="";int lineNumber=0;boolean segment=false,segmentSeen=false;
        for(String original:source.split("\\R")){
            lineNumber++;String line=original.trim().replace('（','(').replace('）',')').replace('，',',');
            if(line.isBlank()||line.startsWith("#")||line.startsWith("//"))continue;
            int comment=line.indexOf("#"),slash=line.indexOf("//");if(slash>=0&&(comment<0||slash<comment))comment=slash;if(comment>=0)line=line.substring(0,comment).trim();
            if(line.matches("segment\\([^,()]+,\\s*define\\)\\s*\\{")){
                if(segmentSeen||!actions.isEmpty())issues.add("第 "+lineNumber+" 行包含多个片段或混合主轴，请单独选择一段检查");segment=true;segmentSeen=true;continue;
            }
            if(segment&&line.equals("}")){segment=false;continue;}
            if(segmentSeen&&!segment)issues.add("第 "+lineNumber+" 行在片段外包含其他逻辑，请单独选择一段检查");
            int space=line.indexOf(' ');
            if(space>0&&aliases.containsKey(line.substring(0,space))){character=aliases.get(line.substring(0,space));line=line.substring(space+1).trim();}
            if(character.isBlank()){issues.add("第 "+lineNumber+" 行缺少可映射的角色名");continue;}
            List<String> commands;
            try{commands=splitCommands(line);}catch(IllegalArgumentException error){issues.add("第 "+lineNumber+" 行："+error.getMessage());continue;}
            for(String command:commands){
                var matcher=Pattern.compile("^(e|q|skill|burst|attack|wait)(?:\\(([^()]*)\\))?$",Pattern.CASE_INSENSITIVE).matcher(command.trim());
                if(!matcher.matches()){issues.add("第 "+lineNumber+" 行暂不支持："+command);continue;}
                String kind=matcher.group(1).toLowerCase(Locale.ROOT),args=Objects.toString(matcher.group(2),"");
                String[] parameters=args.isBlank()?new String[0]:args.split(",");
                boolean timed=kind.equals("attack")||kind.equals("wait");double seconds=0;
                int optionStart=0;
                if(timed){try{if(parameters.length==0)throw new NumberFormatException();seconds=Double.parseDouble(parameters[0]);if(!Double.isFinite(seconds)||seconds<=0||seconds>30)throw new NumberFormatException();optionStart=1;}catch(NumberFormatException error){issues.add("第 "+lineNumber+" 行需要明确 0 至 30 秒的动作时长；不把次数猜成毫秒");continue;}}
                boolean supported=true;
                for(int i=optionStart;i<parameters.length;i++){
                    String option=parameters[i].trim();
                    boolean allowed=option.equals("required")||option.matches("timeout=[0-9.]+")
                            ||(kind.equals("e")||kind.equals("skill"))&&option.equals("wait")
                            ||(kind.equals("q")||kind.equals("burst"))&&option.matches("(?:attempts|no-progress)=[0-9]+");
                    if(!allowed)supported=false;
                }
                if(!supported){issues.add("第 "+lineNumber+" 行含未支持的条件、Buff 记录或动作参数，保留原策略，不自动转换："+command);continue;}
                var action=actions.addObject().put("character",character).put("kind",switch(kind){case "e","skill"->"skill";case "q","burst"->"burst";case "attack"->"attack_seconds";default->"wait";});
                if(timed)action.put("seconds",seconds);
            }
        }
        if(segment)issues.add("片段缺少结束括号");
        if(actions.isEmpty()||actions.size()>80)issues.add("受支持动作数必须为 1 至 80");
        result.put("supported",issues.isEmpty()).put("note","仅转换列明的有限动作；原有等待预算由你选定的执行预设替换。时长普攻会在模拟中完成最后一次攻击，可能越过边界少量帧，不等同实机逐帧对齐。");return result;
    }
    public String nativeScript(JsonNode actions,Map<String,String> names,String preset){
        int timeout=switch(preset){case "relaxed"->16;case "moderate"->10;case "strict"->6;default->throw new IllegalArgumentException("执行预设无效");};
        if(!actions.isArray()||actions.isEmpty()||actions.size()>80)throw new IllegalArgumentException("没有可导出的有限动作序列");
        var output=new StringBuilder("# gcsim 优化候选；离线验证不代表实机可靠性\n# 仍由现有运行器等待、观察、重判和核验动作成功；不赋予手动 Buff 假设\n");
        output.append("segment(start,required,timeout=").append(Math.min(600,actions.size()*(timeout+4))).append(")\n");
        for(JsonNode action:actions){
            String key=action.path("character").asText(),name=names.get(key);if(name==null||name.isBlank()||!name.matches("[\\p{L}0-9·]+"))throw new IllegalArgumentException("BetterGI 缺少角色别名映射："+key);
            String kind=action.path("kind").asText();output.append(name).append(' ');
            switch(kind){
                case "skill"->output.append("e(wait,required,timeout=").append(timeout).append(")");
                case "burst"->output.append("q(required,timeout=").append(timeout).append(")");
                case "attack_seconds","wait"->{double seconds=action.path("seconds").asDouble(Double.NaN);if(!Double.isFinite(seconds)||seconds<=0||seconds>30)throw new IllegalArgumentException("无效动作时长");output.append(kind.equals("wait")?"wait(":"attack(").append(seconds).append(",required)");}
                default->throw new IllegalArgumentException("未知动作不能导出为可执行策略");
            }output.append('\n');
        }
        return output.append("segment(end)\n").toString();
    }
    private static List<String> splitCommands(String text){var commands=new ArrayList<String>();int depth=0,start=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='(')depth++;else if(c==')')depth--;if(depth<0)throw new IllegalArgumentException("括号未配对");if(depth==0&&(c==','||c==';')){commands.add(text.substring(start,i));start=i+1;}}if(depth!=0)throw new IllegalArgumentException("括号未配对");if(start<text.length())commands.add(text.substring(start));return commands;}
}
