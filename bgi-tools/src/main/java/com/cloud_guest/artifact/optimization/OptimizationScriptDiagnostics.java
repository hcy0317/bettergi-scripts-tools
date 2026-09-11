package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;
import static com.cloud_guest.artifact.optimization.OptimizationValidationException.Issue;

/** Source diagnostics only: no rewriting, persistence, or simulator execution. */
public final class OptimizationScriptDiagnostics {
    private static final String IDENT="[\\p{L}_][\\p{L}\\p{N}_（）()]*";
    private static final String BOUNDARY="(?<![\\p{L}\\p{N}_.])";
    private static final Pattern WAIT=Pattern.compile(BOUNDARY+"while\\s+!\\s*\\.("+IDENT+")\\.mods\\.favonius-cd\\s*\\{\\s*("+IDENT+")\\s+attack\\s*;\\s*}");
    private static final Pattern ACTION=Pattern.compile(BOUNDARY+"("+IDENT+")\\s+(?:attack|skill|burst|charge|dash|jump|walk|aim|low_plunge|high_plunge)\\b");
    private static final Pattern ACTIVE=Pattern.compile(BOUNDARY+"active\\s+("+IDENT+")\\s*;");
    private static final Set<String> KEYWORDS=Set.of("fn","let","return","if","else","while","for","switch","case");
    private static final Set<String> FAVONIUS=Set.of("favoniussword","favoniusgreatsword","favoniuslance","favoniuscodex","favoniuswarbow");
    private OptimizationScriptDiagnostics(){}

    public static List<Issue> forBuild(JsonNode workspace,JsonNode build,JsonNode catalog){
        if(build.path("nativeRotation").path("enabled").asBoolean())return List.of();
        var profiles=new HashMap<String,JsonNode>();workspace.path("characters").forEach(p->profiles.put(p.path("key").asText(),p));
        var members=new LinkedHashSet<String>();var weapons=new LinkedHashMap<String,String>();
        for(var member:build.path("members")){
            String key=member.path("character").asText();members.add(key);
            var profile=member.path("profile").isObject()?member.path("profile"):profiles.get(key);
            if(profile!=null)weapons.put(key,profile.path("weapon").asText());
        }
        var aliases=OptimizationLocalization.aliases(catalog,members);
        var issues=new ArrayList<Issue>(diagnose(build.path("rotation").asText(),build.path("id").asText(),"rotation",weapons,aliases));
        if(build.path("scriptPreludeEnabled").asBoolean(true))
            issues.addAll(diagnose(build.path("scriptPrelude").asText(),build.path("id").asText(),"scriptPrelude",weapons,aliases));
        return issues;
    }

    public static List<Issue> diagnose(String source,String buildId,String field,Map<String,String> weapons,Map<String,String> aliases){
        if(source.isBlank())return List.of();
        var issues=new ArrayList<Issue>();
        try{OptimizationScriptParts.split(source);}
        catch(IllegalArgumentException error){
            int line=1;var match=Pattern.compile("第(\\d+)行").matcher(error.getMessage());
            if(match.find())line=Integer.parseInt(match.group(1));
            else line=(int)source.chars().filter(c->c=='\n').count()+1;
            int start=0;for(int i=1;i<line;i++){int next=source.indexOf('\n',start);if(next<0)break;start=next+1;}
            start=Math.min(start,source.length()-1);int end=source.indexOf('\n',start);if(end<0)end=source.length();
            return List.of(issue(source,buildId,field,"",error.getMessage(),start,Math.max(start+1,end)));
        }
        String code=OptimizationScriptParts.maskTriviaAndStrings(source);
        var wait=WAIT.matcher(code);
        while(wait.find()&&issues.size()<100){
            String actor=aliases.getOrDefault(wait.group(1),wait.group(1));
            String attacker=aliases.getOrDefault(wait.group(2),wait.group(2));
            String weapon=weapons.get(actor);
            if(actor.equals(attacker)&&weapon!=null&&!weapon.isBlank()&&!FAVONIUS.contains(weapon))
                issues.add(issue(source,buildId,field,actor,"当前武器 "+weapon+" 无法触发西风效果；请修正这段等待逻辑后再计算",wait.start(1)-1,wait.end(1)+".mods.favonius-cd".length()));
        }
        for(var pattern:List.of(ACTIVE,ACTION)){
            var match=pattern.matcher(code);
            while(match.find()&&issues.size()<100){
                String token=match.group(1),actor=aliases.getOrDefault(token,token);
                if(KEYWORDS.contains(token))continue;
                if(!weapons.containsKey(actor))
                    issues.add(issue(source,buildId,field,actor,"脚本引用的角色 "+token+" 不在当前配队或缺少档案，请修正后再计算",match.start(1),match.end(1)));
            }
        }
        issues.sort(Comparator.comparingInt(Issue::startOffset));
        return issues.stream().distinct().toList();
    }
    private static Issue issue(String source,String buildId,String field,String actor,String message,int start,int end){
        int line=1;for(int i=0;i<start;i++)if(source.charAt(i)=='\n')line++;
        int column=start-source.lastIndexOf('\n',start-1);
        return new Issue("build",actor,buildId,field,message,start,end,line,column);
    }
}
