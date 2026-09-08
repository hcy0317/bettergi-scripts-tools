package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Converts saved user intent plus an existing scan into the immutable engine contract. */
public class OptimizationCompiler {
    @FunctionalInterface public interface MainStats { double value(int rarity,int level,String key); }
    private final ObjectMapper mapper;
    private final MainStats mainStats;
    private final JsonNode catalog;
    public OptimizationCompiler(ObjectMapper mapper,MainStats mainStats){this(mapper,mainStats,null);}
    public OptimizationCompiler(ObjectMapper mapper,MainStats mainStats,JsonNode catalog){this.mapper=mapper;this.mainStats=mainStats;this.catalog=catalog;}
    public ObjectNode compile(JsonNode workspace,ArtifactSnapshot snapshot,JsonNode selection) {
        var validationIssues=OptimizationInputValidation.validate(mapper,workspace,selection,catalog,snapshot);
        if(!validationIssues.isEmpty())throw new OptimizationValidationException(validationIssues);
        // The scan's grid count may include non-equippable enhancement materials.
        // Only submitted, recognized artifact instances enter this task's pool.
        if(snapshot.artifacts().isEmpty())throw new IllegalArgumentException("扫描记录中没有可配装的圣遗物");
        var selected=new TreeSet<String>();selection.path("characters").forEach(c->selected.add(c.asText()));
        if(selected.isEmpty()||selected.size()>16)throw new IllegalArgumentException("请选择 1 至 16 个角色");
        var profiles=index(workspace.path("characters"),"key");
        var builds=index(workspace.path("builds"),"id");
        var ids=new TreeSet<String>();
        for(String key:selected){var profile=required(profiles,key,"角色");if(!profile.path("builds").isArray()||profile.path("builds").isEmpty())throw new IllegalArgumentException(key+" 尚未选择配队 Build");profile.path("builds").forEach(b->ids.add(b.path("id").asText()));}
        var activeKeys=new TreeSet<>(selected);for(String id:ids)required(builds,id,"Build").path("members").forEach(m->activeKeys.add(m.path("character").asText()));
        var owners=catalog==null?null:new OptimizationOwnerResolver(catalog,workspace,activeKeys);
        String mode=selection.path("mode").asText("balanced");
        if(!Set.of("balanced","peak","fallback").contains(mode))throw new IllegalArgumentException("优化档位无效");
        int budget=selection.path("budget").asInt(256);
        if(budget<16||budget>4096||budget<4*ids.size())throw new IllegalArgumentException("计算预算须为 16 至 4096，且至少是场景数的四倍");
        var request=mapper.createObjectNode().put("schemaVersion","1").put("mode",mode).put("evaluationBudget",budget).put("exact",selection.path("exact").asBoolean(false));
        var inventory=request.putObject("inventory");inventory.put("uid",snapshot.uid()).put("scanSessionId",snapshot.scanSessionId()).put("catalogVersion",snapshot.catalogVersion()).put("snapshotDigest",snapshot.snapshotDigest());
        var items=request.putArray("items");
        var current=new HashMap<String,ArrayNode>();
        snapshot.artifacts().forEach(item->{
            var value=items.addObject().put("scanIndex",item.scanIndex()).put("slotKey",item.slotKey()).put("setKey",item.setKey()).put("mainStatKey",item.mainStatKey())
                    .put("mainStatValue",mainStats.value(item.rarity(),item.level(),item.mainStatKey())).put("location",owners==null?canonical(item.location()):owners.resolve(item.location())).put("locked",item.locked()).put("fingerprint",item.contentFingerprint());
            // Dormant is the scanner's explicit current-state marker (for
            // example the pending fourth line at +0), not an activated roll.
            // Keep it in the source fingerprint, but do not grant its stats.
            value.set("substats",mapper.valueToTree(item.substats().stream().filter(stat->!stat.dormant()).toList()));
            if(!item.location().isBlank())current.computeIfAbsent(value.path("location").asText(),ignored->mapper.createArrayNode()).add(item.scanIndex());
        });
        var protections=request.putArray("protectedCharacters");
        if(owners!=null)owners.protectedKeys(workspace).forEach(protections::add);
        else{
            if(!workspace.path("protectedInventoryOwners").isEmpty())throw new IllegalArgumentException("库存角色保护需要已核实的角色身份目录");
            profiles.forEach((key,p)->{if(p.path("protected").asBoolean())protections.add(key);});
        }
        var characters=request.putArray("characters");
        for(String key:selected) {
            var profile=profiles.get(key);
            var c=characters.addObject().put("character",key).put("weight",nonnegative(profile,"weight",1)).put("protected",profile.path("protected").asBoolean());
            c.set("current",current.getOrDefault(key,mapper.createArrayNode()));
            for(String field:List.of("fixedSlots","requiredSets","mainStats","minimumStats","proxyWeights"))if(profile.path(field).isObject())c.set(field,profile.get(field).deepCopy());
            var targets=c.putArray("targets");
            profile.path("builds").forEach(binding->{
                var t=targets.addObject().put("scenario",binding.path("id").asText()).put("metric",binding.path("metric").asText("damage_per_round"))
                        .put("weight",nonnegative(binding,"weight",1)).put("reference",nonnegative(binding,"reference",0));
                if(!Set.of("damage_per_round","effective_healing_per_round").contains(t.path("metric").asText()))throw new IllegalArgumentException("尚未支持该角色目标，请选择可测目标");
            });
        }
        var scenarios=request.putArray("scenarios");
        for(String id:ids) {
            var b=required(builds,id,"Build");
            var s=scenarios.addObject().put("id",id).put("weight",nonnegative(b,"weight",1));
            var evaluation=s.putObject("evaluation");
            var fixed=s.putObject("fixedEquipment");var participants=s.putArray("participants");
            if(!b.path("members").isArray()||b.path("members").isEmpty()||b.path("members").size()>4)throw new IllegalArgumentException("每个配队 Build 需要 1 至 4 名队员");
            var config=new StringBuilder(OptimizationSceneSettings.compile(mapper,b));
            var memberNames=new HashSet<String>();
            for(JsonNode member:b.path("members")) {
                String key=member.path("character").asText();
                if(!memberNames.add(key))throw new IllegalArgumentException("队伍内角色重复");
                JsonNode profile=member.path("profile").isObject()?member.path("profile"):required(profiles,key,"队员档案");
                config.append(personalConfig(key,profile));
                if(selected.contains(key))participants.add(key);
                else if(member.path("kind").asText().equals("real_fixed")) {
                    var equipment=current.get(key);
                    if(equipment==null||equipment.size()!=5)throw new IllegalArgumentException("固定队友 "+key+" 没有可核验的完整实物装备");
                    fixed.set(key,equipment.deepCopy());
                } else if(member.path("kind").asText().equals("hypothetical")) {
                    String stats=member.path("stats").asText("");
                    if(stats.isBlank()||!stats.matches("[a-z0-9%_=+ .-]{1,500}"))throw new IllegalArgumentException("假设队友必须明确填写 gcsim 属性，不能留空冒充真实装备");
                    config.append(key).append(" add stats ").append(stats).append(";\n");
                } else throw new IllegalArgumentException("未参选队友须明确为真实固定装备或假设属性");
            }
            for(String key:selected)if(profiles.get(key).path("builds").findValuesAsText("id").contains(id)&&!memberNames.contains(key))throw new IllegalArgumentException("所选 Build 中缺少角色 "+key);
            String rotation=b.path("rotation").asText("");
            if(rotation.isBlank()||rotation.length()>100_000)throw new IllegalArgumentException("循环内容不能为空或超过大小限制");
            String translated=OptimizationLocalization.translateRotation(rotation,catalog,memberNames);
            OptimizationScriptParts.requireExecutableOnly(translated,id,"rotation");
            String prelude=b.path("scriptPrelude").asText("");
            if(!prelude.isBlank()&&b.path("scriptPreludeEnabled").asBoolean(true)){
                prelude=OptimizationLocalization.translateRotation(prelude,catalog,memberNames);
                OptimizationScriptParts.requireExecutableOnly(prelude,id,"scriptPrelude");config.append(prelude).append('\n');
            }else if(!prelude.isBlank())evaluation.putArray("assumptions").add("auxiliary_logic_disabled");
            boolean autoRounds=b.path("roundPolicy").path("mode").asText().equals("auto");
            if(autoRounds)evaluation.put("autoRounds",true)
                .put("rotationLineOffset",(int)config.toString().chars().filter(c->c=='\n').count())
                .put("mainLoopIndex",OptimizationSceneSettings.integer(b.path("roundPolicy"),"loopIndex",0,64,0,id,"rounds"))
                .put("roundWarmup",OptimizationSceneSettings.integer(b.path("roundPolicy"),"warmup",0,63,0,id,"rounds"));
            config.append(translated);
            evaluation.put("config",config.toString()).put("allowPartial",b.path("allowPartial").asBoolean(false));
            for(String field:List.of("rounds","constraints"))if(b.path(field).isArray()&&(!field.equals("rounds")||!autoRounds))evaluation.set(field,b.get(field).deepCopy());
            var buffs=evaluation.putArray("buffs");for(JsonNode buff:b.path("buffs")){if(!buff.isObject())throw new IllegalArgumentException("Buff 格式无效");if(buff.path("enabled").asBoolean(true)){ObjectNode value=buff.deepCopy();if(catalog!=null&&value.path("relationship").asText().equals("additional")&&!value.path("reviewedEngineRevision").asText().equals(catalog.path("engineRevision").asText()))value.put("relationship","pending_review");value.remove(List.of("enabled","reviewedEngineRevision"));buffs.add(value);}}
        }
        int searchCount=OptimizationSceneSettings.integer(selection,"searchSamples",1,32,3,"","sampling");
        int validationCount=OptimizationSceneSettings.integer(selection,"validationSamples",2,1000,8,"","sampling");
        long[] oldSearch={107,211,307},oldValidation={401,503,601,701,809,907,1009,1103};
        var search=request.putArray("searchSeeds");for(int i=0;i<searchCount;i++)search.add(i<oldSearch.length?oldSearch[i]:1000000L+i);
        var validation=request.putArray("validationSeeds");for(int i=0;i<validationCount;i++)validation.add(i<oldValidation.length?oldValidation[i]:2000000L+i);
        return request;
    }
    private static String personalConfig(String key,JsonNode p) {
        if(!key.matches("[a-z0-9]{1,50}")||!p.path("weapon").asText().matches("[a-z0-9]{1,70}"))throw new IllegalArgumentException("角色或武器键无效，请从 gcsim 目录选择");
        int level=integer(p,"level",1,100),max=integer(p,"maxLevel",level,100);
        int wl=integer(p,"weaponLevel",1,90),wm=integer(p,"weaponMaxLevel",wl,90);
        if(p.path("talents").size()!=3)throw new IllegalArgumentException("请填写三个基础天赋等级");
        var talents=new ArrayList<String>();for(JsonNode v:p.path("talents")){if(!v.isIntegralNumber()||v.asInt()<1||v.asInt()>15)throw new IllegalArgumentException("天赋等级未知或无效");talents.add(v.asText());}
        return key+" char lvl="+level+"/"+max+" cons="+integer(p,"constellation",0,6)+" talent="+String.join(",",talents)+";\n"+key+" add weapon=\""+p.path("weapon").asText()+"\" refine="+integer(p,"refinement",1,5)+" lvl="+wl+"/"+wm+";\n";
    }
    private static Map<String,JsonNode> index(JsonNode values,String field){var map=new LinkedHashMap<String,JsonNode>();for(JsonNode value:values){String key=value.path(field).asText();if(key.isBlank()||map.put(key,value)!=null)throw new IllegalArgumentException("重复或空标识");}return map;}
    private static JsonNode required(Map<String,JsonNode> map,String key,String label){var v=map.get(key);if(v==null)throw new IllegalArgumentException(label+" 不存在："+key);return v;}
    static String canonical(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[_ -]","");}
    private static double nonnegative(JsonNode node,String key,double fallback){if(node.has(key)&&!node.get(key).isNumber())throw new IllegalArgumentException(key+" 必须为数值");double v=node.path(key).asDouble(fallback);if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(key+" 必须为非负有限数");return v;}
    private static int integer(JsonNode node,String key,int min,int max){var v=node.path(key);if(!v.isIntegralNumber()||v.asInt()<min||v.asInt()>max)throw new IllegalArgumentException(key+" 超出有效范围");return v.asInt();}
}
