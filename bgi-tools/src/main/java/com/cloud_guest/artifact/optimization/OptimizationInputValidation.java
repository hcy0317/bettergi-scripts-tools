package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import static com.cloud_guest.artifact.optimization.OptimizationValidationException.Issue;

public final class OptimizationInputValidation {
    private OptimizationInputValidation(){}
    public static List<Issue> validate(ObjectMapper mapper,JsonNode workspace,JsonNode selection,JsonNode catalog,ArtifactSnapshot snapshot){
        var issues=new ArrayList<Issue>();var profiles=index(workspace.path("characters"),"key");var builds=index(workspace.path("builds"),"id");
        var selected=new LinkedHashSet<String>();selection.path("characters").forEach(v->selected.add(v.asText()));var scenarios=new LinkedHashSet<String>();
        if(selected.isEmpty()||selected.size()>16)issues.add(new Issue("selection","","","selection","请选择1至16名参与本次配装的角色，可将当前方案队员加入配装"));
        if(snapshot==null||snapshot.artifacts().isEmpty())issues.add(new Issue("selection","","","snapshot","请选择有效的圣遗物扫描记录"));
        if(catalog!=null)try{
            var owners=new OptimizationOwnerResolver(catalog,workspace,Set.of());
            try{owners.protectedKeys(workspace);}
            catch(IllegalArgumentException error){issues.add(new Issue("selection","","","protections",error.getMessage()));}
            if(snapshot!=null){
                var checkedOwners=new HashSet<String>();
                for(var item:snapshot.artifacts())if(checkedOwners.add(item.location())){
                    try{owners.resolve(item.location());}
                    catch(IllegalArgumentException error){issues.add(new Issue("selection","","","inventoryOwners",error.getMessage()+"；请重新扫描，或修正改名角色的游戏中装备显示名"));}
                }
            }
        }catch(IllegalArgumentException error){issues.add(new Issue("selection","","","inventoryOwners",error.getMessage()));}
        for(String key:selected){JsonNode profile=profiles.get(key);if(profile==null){issues.add(new Issue("character",key,"","profile","所选角色档案不存在"));continue;}personal(profile,key,"",catalog,issues);
            if(!profile.path("builds").isArray()||profile.path("builds").isEmpty())issues.add(new Issue("character",key,"","builds",name(catalog,key)+"尚未关联配队方案"));
            for(JsonNode ref:profile.path("builds")){String id=ref.path("id").asText();scenarios.add(id);if(!builds.containsKey(id))issues.add(new Issue("character",key,"","builds","角色关联的配队方案已不存在"));}
        }
        for(String id:scenarios){JsonNode build=builds.get(id);if(build==null)continue;var members=new LinkedHashSet<String>();
            issues.addAll(OptimizationScriptDiagnostics.forBuild(workspace,build,catalog));
            for(JsonNode m:build.path("members")){String key=m.path("character").asText();if(!members.add(key))issues.add(new Issue("build",key,id,"members","队伍中存在重复角色"));JsonNode p=m.path("profile").isObject()?m.path("profile"):profiles.get(key);
                if(p==null){issues.add(new Issue("build",key,id,"members","队员缺少个人档案"));continue;}
                if(m.has("profile")||!selected.contains(key))personal(p,key,m.has("profile")?id:"",catalog,issues);
                if(!selected.contains(key)&&m.path("kind").asText().equals("hypothetical")&&m.path("stats").asText().isBlank())issues.add(new Issue("build",key,id,"members","假设队友必须填写属性，不能用空白当作真实装备"));
            }
            if(members.isEmpty()||members.size()>4)issues.add(new Issue("build","",id,"members","每队须有1至4名不同队员"));
            for(String key:selected)if(profiles.containsKey(key))for(JsonNode ref:profiles.get(key).path("builds"))if(ref.path("id").asText().equals(id)&&!members.contains(key))issues.add(new Issue("build",key,id,"members",name(catalog,key)+"引用了方案，但不在队伍中"));
            if(build.path("nativeRotation").path("enabled").asBoolean()) {
                try{OptimizationNativeFlow.fromBuild(mapper,build,catalog,members);}
                catch(IllegalArgumentException error){issues.add(new Issue("build","",id,"rotation",error.getMessage()));}
            }else if(build.path("rotation").asText().isBlank())issues.add(new Issue("build","",id,"rotation","请填写或导入循环脚本"));
            try{OptimizationSceneSettings.compile(mapper,build);}catch(OptimizationValidationException e){issues.addAll(e.issues());}
            if(snapshot!=null&&catalog!=null){try{var owners=new OptimizationOwnerResolver(catalog,workspace,members);for(JsonNode m:build.path("members")){String key=m.path("character").asText();if(!selected.contains(key)&&m.path("kind").asText().equals("real_fixed")){
                var slots=new HashSet<String>();long count=0;for(var item:snapshot.artifacts())if(owners.resolve(item.location()).equals(owners.representative(key))){slots.add(item.slotKey());count++;}
                if(count!=5||slots.size()!=5)issues.add(new Issue("build",key,id,"members",name(catalog,key)+"是固定队友，但扫描记录中没有完整五件装备"));
            }}}catch(IllegalArgumentException e){issues.add(new Issue("build","",id,"members",e.getMessage()));}}
        }
        for(String field:List.of("searchSamples","validationSamples")){JsonNode v=selection.path(field);int min=field.equals("searchSamples")?1:2,max=field.equals("searchSamples")?32:1000;if(!v.isMissingNode()&&(!v.isIntegralNumber()||v.asInt()<min||v.asInt()>max))issues.add(new Issue("selection","","","sampling","随机采样次数未填写或超出范围"));}
        return issues.stream().distinct().toList();
    }
    private static Map<String,JsonNode> index(JsonNode array,String field){var result=new LinkedHashMap<String,JsonNode>();for(JsonNode v:array)result.put(v.path(field).asText(),v);return result;}
    private static String name(JsonNode catalog,String key){return OptimizationLocalization.name(catalog,"character_names",key);}
    private static void personal(JsonNode p,String key,String build,JsonNode catalog,List<Issue> issues){
        String scope=build.isEmpty()?"character":"build",name=name(catalog,key);
        if(catalog!=null&&catalog.path("characters").isArray()){
            boolean known=false;for(JsonNode character:catalog.path("characters"))if(character.path("key").asText().equals(key))known=true;
            if(!known)issues.add(new Issue(scope,key,build,"profile",name+"：当前引擎没有此角色的模拟实现；库存身份可用不等于可以参战计算"));
        }
        String[] fields={"level","maxLevel","constellation","weaponLevel","weaponMaxLevel","refinement"},labels={"角色等级","突破上限","命座","武器等级","武器突破上限","精炼"};int[] min={1,Math.max(1,p.path("level").asInt()),0,1,Math.max(1,p.path("weaponLevel").asInt()),1},max={100,100,6,90,90,5};
        for(int i=0;i<fields.length;i++){var v=p.path(fields[i]);if(!v.isIntegralNumber()||!v.canConvertToInt()||v.asInt()<min[i]||v.asInt()>max[i])issues.add(new Issue(scope,key,build,fields[i],name+"："+labels[i]+"未填写或超出范围"));}
        String weapon=p.path("weapon").asText();boolean known=!weapon.isBlank();if(catalog!=null&&catalog.path("weapons").isArray()&&!catalog.path("weapons").isEmpty()){known=false;for(JsonNode w:catalog.path("weapons"))if(w.path("key").asText().equals(weapon))known=true;}
        for(String field:List.of("maxLevel","weaponMaxLevel")){var cap=p.path(field);if(!cap.isIntegralNumber()||cap.asInt()<10||cap.asInt()>90||cap.asInt()%10!=0)issues.add(new Issue(scope,key,build,field,name+"：等级上限须选择10至90之间的整十档位"));}
        if(!known)issues.add(new Issue(scope,key,build,"weapon",name+"：请选择有效武器"));
        var talents=p.path("talents");boolean valid=talents.isArray()&&talents.size()==3;for(JsonNode v:talents)if(!v.isIntegralNumber()||v.asInt()<1||v.asInt()>15)valid=false;if(!valid)issues.add(new Issue(scope,key,build,"talents",name+"：请补全三个基础天赋等级"));
    }
}
