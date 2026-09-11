package com.cloud_guest.artifact.optimization;

import java.util.*;

/** Lexical boundaries only. gcsim remains the authority for executable syntax. */
public final class OptimizationScriptParts {
    private static final Set<String> FAVONIUS_WEAPONS=Set.of("favoniussword","favoniusgreatsword","favoniuslance","favoniuscodex","favoniuswarbow");
    private static final java.util.regex.Pattern PURE_FAVONIUS_WAIT=java.util.regex.Pattern.compile("\\bwhile\\s+!\\s*\\.([a-z0-9]+)\\.mods\\.favonius-cd\\s*\\{\\s*\\1\\s+attack\\s*;\\s*}");
    public record Statement(String raw,String code,int line){}
    public record WeaponWaitAdaptation(String config,List<String> assumptions){}
    private OptimizationScriptParts(){}
    /** Historical offline diagnostic projection only; production task admission never calls this. */
    public static WeaponWaitAdaptation adaptImpossibleWeaponWaits(String source,Map<String,String> weapons){
        split(source); // validate lexical balance before any projection
        String masked=maskTriviaAndStrings(source);
        record Span(int start,int end,String actor){}
        var spans=new ArrayList<Span>();var matcher=PURE_FAVONIUS_WAIT.matcher(masked);
        while(matcher.find())spans.add(new Span(matcher.start(),matcher.end(),matcher.group(1)));
        if(spans.isEmpty()||source.contains("\"favonius-cd\"")||source.contains("'favonius-cd'"))return new WeaponWaitAdaptation(source,List.of());
        var references=java.util.regex.Pattern.compile("favonius-cd").matcher(masked);
        while(references.find()){
            int at=references.start();
            if(spans.stream().noneMatch(s->at>=s.start()&&at<s.end()))return new WeaponWaitAdaptation(source,List.of());
        }
        char[] projected=source.toCharArray();var assumptions=new LinkedHashSet<String>();
        for(var span:spans){
            String weapon=weapons.get(span.actor());
            if(weapon==null||FAVONIUS_WEAPONS.contains(weapon))continue;
            for(int i=span.start();i<span.end();i++)if(projected[i]!='\n'&&projected[i]!='\r')projected[i]=' ';
            assumptions.add("removed_impossible_favonius_wait:"+span.actor());
        }
        return new WeaponWaitAdaptation(new String(projected),List.copyOf(assumptions));
    }

    static String maskTriviaAndStrings(String source){
        char[] masked=source.toCharArray();char quote=0;boolean line=false,block=false,escape=false;
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;
            if(line){if(c=='\n')line=false;else masked[i]=' ';continue;}
            if(block){if(c!='\n'&&c!='\r')masked[i]=' ';if(c=='*'&&next=='/'){masked[++i]=' ';block=false;}continue;}
            if(quote!=0){if(c!='\n'&&c!='\r')masked[i]=' ';if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='#'||c=='/'&&next=='/'){line=true;masked[i]=' ';if(c=='/')masked[++i]=' ';}
            else if(c=='/'&&next=='*'){block=true;masked[i]=' ';masked[++i]=' ';}
            else if(c=='\"'||c=='\''){quote=c;masked[i]=' ';}
        }
        return new String(masked);
    }
    public static List<Statement> split(String source){
        if(source==null||source.length()>100_000)throw new IllegalArgumentException("脚本为空或超过100 KB");
        var result=new ArrayList<Statement>();var code=new StringBuilder();int start=0,line=1,startLine=1,braces=0,parens=0,brackets=0;char quote=0;boolean lineComment=false,blockComment=false,escape=false;
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;
            if(c=='\n')line++;
            if(lineComment){if(c=='\n'){lineComment=false;code.append('\n');}continue;}
            if(blockComment){if(c=='*'&&next=='/'){blockComment=false;i++;code.append(' ');}continue;}
            if(quote!=0){code.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='#'||c=='/'&&next=='/'){lineComment=true;if(c=='/')i++;continue;}
            if(c=='/'&&next=='*'){blockComment=true;i++;continue;}
            code.append(c);
            if(c=='"'||c=='\''){quote=c;continue;}
            if(c=='(')parens++;if(c==')')parens--;if(c=='[')brackets++;if(c==']')brackets--;if(c=='{')braces++;if(c=='}')braces--;
            if(Math.min(braces,Math.min(parens,brackets))<0)throw new IllegalArgumentException("脚本第"+line+"行括号未配对");
            if(braces!=0||parens!=0||brackets!=0)continue;
            if(c!=';'&&c!='}')continue;
            boolean block=code.toString().stripLeading().matches("(?s)^(for|while|if|fn|switch)\\b.*");
            boolean end=c==';'&&!block||c=='}'&&block&&!source.startsWith("else",skipTrivia(source,i+1));
            if(end){result.add(new Statement(source.substring(start,i+1),code.toString().strip(),startLine));start=i+1;startLine=line;code.setLength(0);}
        }
        if(quote!=0||blockComment||braces!=0||parens!=0||brackets!=0)throw new IllegalArgumentException("脚本结尾存在未闭合的字符串、注释或括号");
        if(!code.toString().isBlank())result.add(new Statement(source.substring(start),code.toString().strip(),startLine));
        return result;
    }
    private static int skipTrivia(String source,int index){
        while(index<source.length()){
            if(Character.isWhitespace(source.charAt(index))){index++;continue;}
            if(source.charAt(index)=='#'||source.startsWith("//",index)){int end=source.indexOf('\n',index);if(end<0)return source.length();index=end+1;continue;}
            if(source.startsWith("/*",index)){int end=source.indexOf("*/",index+2);if(end<0)return source.length();index=end+2;continue;}
            break;
        }return index;
    }
    public static boolean isDeclaration(String code){return code.matches("(?s)^(options|target|energy|hurt|player)\\b.*")||code.matches("(?s)^[\\p{L}_][\\p{L}\\p{N}_]*\\s+(char|add)\\b.*");}
    public static void requireExecutableOnly(String source,String id,String field){for(var statement:split(source))if(isDeclaration(statement.code()))throw OptimizationValidationException.scene(id,field,"脚本第"+statement.line()+"行仍包含角色或场景设置，请使用导入预览拆分到对应区域");}
    public static void requireCompatibleWeaponWaits(String source,String id,Map<String,String> weapons) {
        for(var statement:split(source)) {
            String code=statement.code().replaceAll("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"," ");
            var wait=PURE_FAVONIUS_WAIT.matcher(code);
            while(wait.find()) {
                String actor=wait.group(1),weapon=weapons.get(actor);
                if(weapon!=null&&!FAVONIUS_WEAPONS.contains(weapon))
                    throw OptimizationValidationException.scene(id,"rotation",actor+" 的循环在等待西风武器触发，但当前武器为 "+weapon+"。该脚本不能安全自动改写，请切换为对应00原生流程或明确修正循环；系统不会换掉个人武器。");
            }
        }
    }
}
