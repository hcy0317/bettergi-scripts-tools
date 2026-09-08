package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.regex.Pattern;

/** Source-preserving input model. It never executes native keyboard/mouse commands. */
public final class OptimizationNativeFlow {
    public static final String MACRO = "neuvillette_charge_v1";
    private final ObjectMapper mapper;
    private final Map<String,String> aliases;
    private final String source;
    private int sequence;
    private String actor = "";
    private record Token(String text, int start, int end, int line, int column) {}

    public OptimizationNativeFlow(ObjectMapper mapper, String source, Map<String,String> aliases) {
        this.mapper=mapper; this.source=source; this.aliases=aliases;
    }
    public static boolean recognizes(String source) {
        return source!=null && Pattern.compile("(?m)^\\s*(strategy|branch|call)\\s*[（(]").matcher(source).find();
    }
    public static ObjectNode fromBuild(ObjectMapper mapper,JsonNode build,JsonNode catalog,Set<String> members) {
        JsonNode nativeInput=build.path("nativeRotation");
        if(!nativeInput.path("enabled").asBoolean(false))return null;
        if(!nativeInput.path("enabled").isBoolean())throw new IllegalArgumentException("原生流程启用状态无效");
        if(catalog==null||!catalog.path("capabilities").path("nativeFlow").path("schemaVersion").asText().equals("native-flow-v1"))throw new IllegalArgumentException("当前计算引擎尚未安装原生流程适配，请更新引擎后重试");
        var program=new OptimizationNativeFlow(mapper,nativeInput.path("source").asText(),OptimizationLocalization.aliases(catalog,members)).program();
        boolean macro=false;for(JsonNode block:program.path("blocks"))if(block.has("macro"))macro=true;
        if(macro&&!nativeInput.path("macroMapping").asText().equals(MACRO))throw new IllegalArgumentException("请确认喷射宏按gcsim标准重击进行带假设试算");
        if(!build.path("scriptPrelude").asText().isBlank()&&build.path("scriptPreludeEnabled").asBoolean(true))throw new IllegalArgumentException("原生流程不能同时运行gcsim辅助脚本；请明确停用辅助逻辑，或切回gcsim循环");
        return program;
    }
    public static String initialCharacter(JsonNode program) {
        // Choose a reachable actor in control-flow order. Branches themselves
        // still execute their guards at runtime; unrelated definitions never set active.
        String key=firstCharacter(program.path("root"),program.path("blocks"),new HashSet<>());
        if(key.isBlank())throw new IllegalArgumentException("原生流程没有可执行角色动作");
        return key;
    }
    private static String firstCharacter(JsonNode nodes,JsonNode blocks,Set<String> visited) {
        for(JsonNode node:nodes) {
            String kind=node.path("kind").asText();
            if(kind.equals("call")){String target=node.path("args").path(0).asText();if(visited.add(target)){String key=firstCharacter(blocks.path(target).path("nodes"),blocks,visited);if(!key.isBlank())return key;}}
            else if(kind.equals("branch")){
                for(String arm:List.of("then","else","unknown")){
                    String target=node.path("options").path(arm).asText();
                    if(!target.isBlank()&&visited.add(target)){String key=firstCharacter(blocks.path(target).path("nodes"),blocks,visited);if(!key.isBlank())return key;}
                }
            }
            else if(!kind.equals("branch")&&!node.path("character").asText().isBlank())return node.path("character").asText();
        }
        return "";
    }
    public ObjectNode parse() {
        var response=mapper.createObjectNode().put("mode","native_flow").put("simulationOnly",true);
        var issues=response.putArray("issues"); response.putArray("actions");
        try {
            ObjectNode program=program(); response.set("program",program); response.put("supported",true);
            response.put("note","原流程及守卫完整保留。使用模拟能量/冷却，视觉观测保持未知；喷射宏映射为gcsim标准重击。结果仅为带假设试算，不证明实机等价。");
        } catch(IllegalArgumentException error) {response.put("supported",false); issues.add(error.getMessage());}
        return response;
    }
    public ObjectNode program() {
        sequence=0;actor="";
        if(source==null||source.isBlank()||source.length()>100_000)throw new IllegalArgumentException("原生策略为空或超过100000字符");
        var program=mapper.createObjectNode().put("schemaVersion","native-flow-v1").put("source",source).put("loop",false);
        ArrayNode root=program.putArray("root"); ObjectNode blocks=program.putObject("blocks"),timings=program.putObject("timings");
        ArrayNode nodes=root; ObjectNode open=null; boolean expectBrace=false,executable=false,strategySeen=false;
        for(Token token:tokens()) {
            if(token.text.equals("{")) {if(!expectBrace)throw bad(token,"没有对应的片段声明"); expectBrace=false; continue;}
            if(expectBrace)throw bad(token,"片段声明后需要左花括号");
            if(token.text.equals("}")) {if(open==null)throw bad(token,"多余的右花括号");open.put("end",token.end);open=null;nodes=root;actor="";continue;}
            ObjectNode command=command(token);String kind=command.path("kind").asText();
            ObjectNode options=(ObjectNode)command.get("options");JsonNode args=command.path("args");
            if(kind.equals("strategy")) {
                if(executable||strategySeen||!args.isEmpty()||!options.path("loop").asText().equals("battle"))throw bad(token,"strategy(loop=battle)须在开头且只能声明一次");
                strategySeen=true;program.put("loop",true);continue;
            }
            if(kind.equals("timing")) {
                if(executable||args.size()!=1)throw bad(token,"timing须在开头声明一个名称");
                String name=args.get(0).asText();if(timings.has(name))throw bad(token,"重复timing："+name);
                timings.set(name,options);continue;
            }
            executable=true;
            if(kind.equals("segment")) {
                if(open!=null||args.size()!=2||!args.get(1).asText().equals("define"))throw bad(token,"此适配需要命名的segment(名称,define)定义段，不能嵌套定义");
                String name=args.get(0).asText();if(blocks.has(name))throw bad(token,"重复片段："+name);
                open=blocks.putObject(name);open.set("declaration",command);nodes=open.putArray("nodes");expectBrace=true;actor="";
                continue;
            }
            nodes.add(command);
        }
        if(open!=null||expectBrace)throw new IllegalArgumentException("片段缺少结束花括号");
        if(root.isEmpty())throw new IllegalArgumentException("策略没有可执行主轴；定义片段需要由call或branch调用");
        if(blocks.size()>32)throw new IllegalArgumentException("最多支持32个命名片段");
        for(JsonNode block:blocks) {
            boolean input=false;for(JsonNode n:block.path("nodes"))if(Set.of("keydown","keyup","moveby").contains(n.path("kind").asText()))input=true;
            if(input) {
                if(!macro(block))throw nodeError(block.path("declaration"),"输入宏不能可靠映射；仅支持同一那维莱特的完整左键按住/移动/松开atomic块");
                ((ObjectNode)block).put("macro",MACRO);
            }
        }
        validate(program);
        return program;
    }
    public String referenceSource(JsonNode changes) {
        ObjectNode program=program();var tunable=new LinkedHashMap<String,JsonNode>();var locked=new HashSet<String>();
        program.path("blocks").fields().forEachRemaining(e->{if(e.getValue().has("macro")||e.getValue().path("declaration").path("options").has("atomic"))lockBlock(e.getKey(),program.path("blocks"),locked);});
        for(JsonNode n:program.path("root"))if(Set.of("wait","attack").contains(n.path("kind").asText()))tunable.put(n.path("id").asText(),n);
        program.path("blocks").fields().forEachRemaining(e->{if(!locked.contains(e.getKey()))for(JsonNode n:e.getValue().path("nodes"))if(Set.of("wait","attack").contains(n.path("kind").asText()))tunable.put(n.path("id").asText(),n);});
        if(!changes.isArray()||changes.size()>tunable.size())throw new IllegalArgumentException("参考候选的参数变更无效");
        record Replacement(int start,int end,String value){}
        var replacements=new ArrayList<Replacement>();var seen=new HashSet<String>();
        for(JsonNode change:changes) {
            String id=change.path("node").asText();JsonNode n=tunable.get(id);
            if(n==null||!seen.add(id))throw new IllegalArgumentException("参考候选试图修改守卫、宏或重复参数："+id);
            double original=n.path("seconds").asDouble(),value=change.path("value").asDouble(Double.NaN);
            if(!change.path("original").isNumber()||change.path("original").asDouble()!=original||!change.path("value").isNumber()||!Double.isFinite(value)||value<Math.max(.05,original-.5)-1e-9||value>Math.min(30,original+.5)+1e-9)throw nodeError(n,"数值变更超出原参数边界或基线已改变");
            int start=n.path("valueStart").asInt(-1),end=n.path("valueEnd").asInt(-1);
            if(start<0||end<=start||end>source.length()||Double.parseDouble(source.substring(start,end))!=original)throw nodeError(n,"源码位置与原数值不一致");
            replacements.add(new Replacement(start,end,java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()));
        }
        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());var output=new StringBuilder(source);
        for(var replacement:replacements)output.replace(replacement.start(),replacement.end(),replacement.value());
        String result=output.toString();new OptimizationNativeFlow(mapper,result,aliases).program();return result;
    }
    private static void lockBlock(String name,JsonNode blocks,Set<String> locked) {
        if(!locked.add(name))return;JsonNode block=blocks.path(name);
        for(String target:targets(block.path("declaration")))lockBlock(target,blocks,locked);
        for(JsonNode n:block.path("nodes"))for(String target:targets(n))lockBlock(target,blocks,locked);
    }
    private List<Token> tokens() {
        var result=new ArrayList<Token>();int begin=-1,depth=0,line=1,column=1,startLine=1,startColumn=1;
        for(int i=0;i<=source.length();i++) {
            char raw=i==source.length()?'\n':source.charAt(i),c=normalize(raw);
            if(depth==0&&(c=='#'||c=='/'&&i+1<source.length()&&source.charAt(i+1)=='/')) {
                if(begin>=0){result.add(new Token(source.substring(begin,i).trim(),begin,i,startLine,startColumn));begin=-1;}
                while(i<source.length()&&source.charAt(i)!='\n'){i++;column++;}c='\n';raw='\n';
            }
            if(depth==0&&(c=='\n'||c=='\r'||c==','||c==';'||c=='{'||c=='}')) {
                if(begin>=0){result.add(new Token(source.substring(begin,i).trim(),begin,i,startLine,startColumn));begin=-1;}
                if(c=='{'||c=='}')result.add(new Token(String.valueOf(c),i,i+1,line,column));
            } else {
                if(begin<0&&!Character.isWhitespace(c)&&c!='\uFEFF'){begin=i;startLine=line;startColumn=column;}
                if(c=='('&&++depth>32)throw new IllegalArgumentException("第"+line+"行括号嵌套超过32层");
                if(c==')'&&--depth<0)throw new IllegalArgumentException("第"+line+"行括号未配对");
            }
            if(raw=='\n'){line++;column=1;}else column++;
            if(result.size()>600)throw new IllegalArgumentException("策略命令数量超过600");
        }
        if(depth!=0)throw new IllegalArgumentException("策略括号未配对");
        return result;
    }
    private ObjectNode command(Token token) {
        String text=normalized(token.text);int space=text.indexOf(' '),open=text.indexOf('('),offset=0;
        if(space>0&&(open<0||space<open)) {
            String possible=text.substring(0,space);
            if(aliases.containsKey(possible)) {actor=aliases.get(possible);if(actor==null||actor.isBlank())throw bad(token,"角色别名存在歧义："+possible);offset=space+1;while(offset<text.length()&&Character.isWhitespace(text.charAt(offset)))offset++;text=text.substring(offset);}
        }
        var matcher=Pattern.compile("^([a-z-]+)(?:\\((.*)\\))?$",Pattern.DOTALL).matcher(text);
        if(!matcher.matches())throw bad(token,"无法识别命令或队伍角色："+text);
        String kind=switch(matcher.group(1)){case "e"->"skill";case "q"->"burst";default->matcher.group(1);};
        if(!Set.of("strategy","timing","segment","call","branch","skill","burst","attack","wait","check","keydown","keyup","moveby").contains(kind))throw bad(token,"未支持的动作："+kind);
        if(!Set.of("strategy","timing","segment","call","branch").contains(kind)&&actor.isBlank())throw bad(token,"缺少本队可映射的角色名");
        var node=mapper.createObjectNode().put("id","n"+(++sequence)).put("kind",kind).put("character",actor).put("line",token.line).put("column",token.column).put("start",token.start).put("end",token.end);
        var args=node.putArray("args");var options=node.putObject("options");
        String argument=Objects.toString(matcher.group(2),"");
        for(String item:split(argument)) {
            int equal=item.indexOf('=');
            if(equal>0) {String key=item.substring(0,equal).trim(),value=item.substring(equal+1).trim();if(value.isBlank()||options.has(key))throw bad(token,"重复或空参数："+key);options.put(key,value);}
            else if(Set.of("required","hold","fast","wait","atomic").contains(item)){if(options.has(item))throw bad(token,"重复参数："+item);options.put(item,"true");}
            else args.add(item);
        }
        Set<String> allowed=switch(kind) {
            case "strategy"->Set.of("loop");case "timing"->Set.of("cd","duration");
            case "segment"->Set.of("required","atomic","timeout","record","requires","onfail");
            case "call"->Set.of("required","if","once","timeout","attempts");
            case "branch"->Set.of("if","then","else","unknown","required");
            case "skill"->Set.of("hold","fast","wait","required","timeout","if","record","maintain","watch","watch-mode","watch-target","before","timing","keep","feed");
            case "burst"->Set.of("required","timeout","attempts","no-progress","if","keep","record","timing");
            case "attack","wait"->Set.of("required","timeout","if","keep");
            case "keydown"->Set.of("required","keep");default->Set.of();
        };
        options.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw bad(token,"动作"+kind+"未支持参数："+key);});
        for(String field:List.of("timeout","duration","cd","before"))if(options.has(field))number(options.path(field).asText(),field,field.equals("cd")||field.equals("before")?0:0.01,600,token);
        for(String field:List.of("attempts","no-progress"))if(options.has(field)){double value=number(options.path(field).asText(),field,1,64,token);if(value!=Math.rint(value))throw bad(token,field+"须为整数");}
        for(String field:List.of("record","keep","maintain","watch","watch-target","onfail","then","else","unknown","timing"))if(options.has(field))name(options.path(field).asText(),token);
        if(options.has("if"))node.set("condition",new Condition(options.path("if").asText(),actor,token).parse());
        if(options.has("requires"))node.set("requires",new Condition(options.path("requires").asText(),actor,token).parse());
        if(options.has("feed")){String receiver=aliases.get(options.path("feed").asText());if(receiver==null||receiver.isBlank())throw bad(token,"接球队员不在当前队伍");options.put("feed",receiver);}
        if(options.has("once")&&!options.path("once").asText().equals("battle"))throw bad(token,"once只支持battle");
        if(kind.equals("branch")&&(!options.has("if")||!options.has("then")||!args.isEmpty()))throw bad(token,"branch需要if和then，不能含位置参数");
        if(kind.equals("call")&&(args.size()!=1||!validName(args.path(0).asText())))throw bad(token,"call需要一个合法片段名");
        if(Set.of("skill","burst","check").contains(kind)&&!args.isEmpty())throw bad(token,"动作不接受这些位置参数");
        if(kind.equals("attack")||kind.equals("wait")) {
            if(args.size()!=1)throw bad(token,"普攻/等待需要一个明确秒数");double seconds=number(args.get(0).asText(),"秒数",0.01,30,token);node.put("seconds",seconds);
            int valueStart=token.start+offset+text.indexOf('(')+1;while(valueStart<source.length()&&Character.isWhitespace(source.charAt(valueStart)))valueStart++;
            node.put("valueStart",valueStart).put("valueEnd",valueStart+args.get(0).asText().length());
        }
        return node;
    }
    private void validate(ObjectNode program) {
        JsonNode blocks=program.path("blocks"),timings=program.path("timings");var edges=new LinkedHashMap<String,Set<String>>();
        var all=new ArrayList<JsonNode>();program.path("root").forEach(all::add);for(JsonNode block:blocks){all.add(block.path("declaration"));block.path("nodes").forEach(all::add);}
        var records=new HashSet<String>();for(JsonNode n:all)if(n.path("options").has("record"))records.add(n.path("options").path("record").asText());
        for(JsonNode n:all) {
            JsonNode o=n.path("options");
            for(String key:List.of("keep","maintain","watch"))if(o.has(key)&&!records.contains(o.path(key).asText()))throw nodeError(n,"未定义记录："+o.path(key).asText());
            validateConditionRecords(n.path("condition"),records,n);validateConditionRecords(n.path("requires"),records,n);
            if(o.has("timing")&&!timings.has(o.path("timing").asText()))throw nodeError(n,"未声明timing："+o.path("timing").asText());
            if(o.has("watch")&&(!o.path("watch-mode").asText().equals("call")||!o.has("watch-target")||!o.path("watch").asText().equals(o.path("record").asText())||!o.path("watch").asText().equals(o.path("maintain").asText())))throw nodeError(n,"维护观察须绑定同一record/maintain并声明watch-mode=call和watch-target");
            if(!o.has("watch")&&(o.has("watch-mode")||o.has("watch-target")))throw nodeError(n,"维护目标缺少watch声明");
            for(String target:targets(n))if(!blocks.has(target))throw nodeError(n,"未定义片段："+target);
            if(Set.of("keydown","keyup","moveby").contains(n.path("kind").asText())&&java.util.stream.StreamSupport.stream(program.path("root").spliterator(),false).anyMatch(r->r==n))throw nodeError(n,"输入宏必须在完整atomic定义段中");
        }
        blocks.fields().forEachRemaining(e->{var target=new LinkedHashSet<String>(targets(e.getValue().path("declaration")));e.getValue().path("nodes").forEach(n->target.addAll(targets(n)));edges.put(e.getKey(),target);});
        for(String block:edges.keySet())cycle(block,edges,new HashSet<>(),new HashSet<>());
    }
    private static void validateConditionRecords(JsonNode condition,Set<String> records,JsonNode node) {
        if(condition.isMissingNode())return;
        if(condition.path("name").asText().startsWith("record-")&&!records.contains(condition.path("argument").asText()))throw nodeError(node,"未定义记录："+condition.path("argument").asText());
        validateConditionRecords(condition.path("left"),records,node);validateConditionRecords(condition.path("right"),records,node);
    }
    private static Set<String> targets(JsonNode n) {
        var result=new LinkedHashSet<String>();if(n.path("kind").asText().equals("call"))result.add(n.path("args").path(0).asText());
        for(String key:List.of("then","else","unknown","onfail","watch-target"))if(n.path("options").has(key))result.add(n.path("options").path(key).asText());return result;
    }
    private static void cycle(String name,Map<String,Set<String>> graph,Set<String> visiting,Set<String> done) {
        if(done.contains(name))return;if(!visiting.add(name))throw new IllegalArgumentException("片段调用/恢复/维护存在循环依赖："+name);
        for(String next:graph.getOrDefault(name,Set.of()))cycle(next,graph,visiting,done);visiting.remove(name);done.add(name);
    }
    private boolean macro(JsonNode block) {
        JsonNode nodes=block.path("nodes");if(!block.path("declaration").path("options").has("atomic")||nodes.size()<3)return false;
        for(int i=0;i<nodes.size();i++) {
            JsonNode n=nodes.get(i);String kind=n.path("kind").asText();if(!n.path("character").asText().equals("neuvillette"))return false;
            if(i==0||i==nodes.size()-1) {if(!kind.equals(i==0?"keydown":"keyup")||n.path("args").size()!=1||!n.path("args").get(0).asText().equals("VK_LBUTTON"))return false;}
            else if(kind.equals("wait")) {if(!n.path("options").isEmpty())return false;}
            else if(kind.equals("moveby")) {if(n.path("args").size()!=2||!n.path("options").isEmpty())return false;for(JsonNode v:n.path("args"))try{double value=Double.parseDouble(v.asText());if(!Double.isFinite(value)||Math.abs(value)>10000)return false;}catch(NumberFormatException error){return false;}}
            else return false;
        }
        return true;
    }
    private final class Condition {
        final String text,character;final Token token;int position,depth;
        Condition(String text,String character,Token token){this.text=text;this.character=character;this.token=token;}
        ObjectNode parse(){
            ObjectNode n=or();space();if(position!=text.length())throw bad(token,"条件中含未支持语义："+text.substring(position));
            record Entry(JsonNode node,int level){}
            var pending=new ArrayDeque<Entry>();pending.push(new Entry(n,1));int count=0;
            while(!pending.isEmpty()){
                var entry=pending.pop();if(entry.level()>32||++count>128)throw bad(token,"条件树超过32层或128个节点，请拆分为多个明确片段");
                for(String side:List.of("left","right"))if(entry.node().has(side))pending.push(new Entry(entry.node().get(side),entry.level()+1));
            }
            return n;
        }
        ObjectNode or(){ObjectNode left=and();while(take("||"))left=binary("or",left,and());return left;}
        ObjectNode and(){ObjectNode left=atom();while(take("&&"))left=binary("and",left,atom());return left;}
        ObjectNode atom(){if(++depth>32)throw bad(token,"条件嵌套超过32层");try{return primary();}finally{depth--;}}
        ObjectNode primary(){if(take("!")){var n=mapper.createObjectNode().put("op","not");n.set("left",atom());return n;}if(take("(")){var n=or();if(!take(")"))throw bad(token,"条件括号未配对");return n;}
            space();int start=position;while(position<text.length()&&(Character.isLetter(text.charAt(position))||text.charAt(position)=='-'))position++;
            String function=text.substring(start,position);if(!take("("))throw bad(token,"无效条件函数："+function);int argStart=position;while(position<text.length()&&text.charAt(position)!=')')position++;if(position==text.length())throw bad(token,"条件缺少右括号");String arg=text.substring(argStart,position++).trim();
            if(!Set.of("q-ready","q-energy-low","q-cd","e-ready","e-cd","low-hp","record-active","record-exists").contains(function))throw bad(token,"尚未支持条件："+function);
            if(function.startsWith("record-"))name(arg,token);else {arg=arg.isEmpty()?character:aliases.get(arg);if(arg==null||arg.isBlank())throw bad(token,"条件角色不在当前队伍");}
            return mapper.createObjectNode().put("op","call").put("name",function).put("argument",arg);
        }
        ObjectNode binary(String op,ObjectNode left,ObjectNode right){var n=mapper.createObjectNode().put("op",op);n.set("left",left);n.set("right",right);return n;}
        void space(){while(position<text.length()&&Character.isWhitespace(text.charAt(position)))position++;}
        boolean take(String s){space();if(!text.startsWith(s,position))return false;position+=s.length();return true;}
    }
    private static List<String> split(String input) {var result=new ArrayList<String>();int start=0,depth=0;for(int i=0;i<input.length();i++){char c=input.charAt(i);if(c=='(')depth++;if(c==')')depth--;if(c==','&&depth==0){result.add(input.substring(start,i).trim());start=i+1;}}if(start<input.length())result.add(input.substring(start).trim());return result;}
    private static double number(String raw,String label,double min,double max,Token token){try{double value=Double.parseDouble(raw);if(!Double.isFinite(value)||value<min||value>max)throw new NumberFormatException();return value;}catch(NumberFormatException error){throw bad(token,label+"超出范围或格式错误");}}
    private static boolean validName(String name){return name.matches("[\\p{L}\\p{N}_·-]{1,60}");}
    private static void name(String value,Token token){if(!validName(value))throw bad(token,"名称无效："+value);}
    private static char normalize(char value){return switch(value){case '（'->'(';case '）'->')';case '，'->',';default->value;};}
    private static String normalized(String value){return value.replace('（','(').replace('）',')').replace('，',',');}
    private static IllegalArgumentException bad(Token token,String message){return new IllegalArgumentException("第"+token.line+"行第"+token.column+"列："+message);}
    private static IllegalArgumentException nodeError(JsonNode node,String message){return new IllegalArgumentException("第"+node.path("line").asInt()+"行："+message);}
}
