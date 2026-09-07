package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Display metadata and a lexical adapter; canonical engine identity never changes. */
public final class OptimizationLocalization {
    private OptimizationLocalization() {}
    public static String name(JsonNode catalog,String category,String key) {
        if(catalog!=null){
            String value=catalog.path("localization").path(category).path(OptimizationCompiler.canonical(key)).asText();
            if(!value.isBlank())return value;
            if(category.equals("character_names"))for(JsonNode c:catalog.path("characters"))if(c.path("key").asText().equals(key)&&!c.path("nativeName").asText().isBlank())return c.path("nativeName").asText();
        }
        return key;
    }
    public static Map<String,String> aliases(JsonNode catalog,Set<String> members) {
        var aliases=new LinkedHashMap<String,String>();
        for(String key:members){
            add(aliases,key,key);add(aliases,name(catalog,"character_names",key),key);
            if(catalog!=null)for(JsonNode c:catalog.path("characters"))if(c.path("key").asText().equals(key)){
                add(aliases,c.path("nativeName").asText(),key);
                for(JsonNode alias:c.path("inventoryAliases"))add(aliases,alias.asText(),key);
            }
        }
        return Collections.unmodifiableMap(aliases);
    }
    private static void add(Map<String,String> map,String alias,String key){
        if(alias.isBlank())return;
        map.merge(alias,key,(previous,current)->previous.equals(current)?current:"");
        String compact=alias.replace(" ","").replace('(', '（').replace(')', '）');
        if(!compact.equals(alias))map.merge(compact,key,(previous,current)->previous.equals(current)?current:"");
    }
    public static String translateRotation(String source,JsonNode catalog,Set<String> members) {
        var aliases=aliases(catalog,members);
        var chinese=aliases.keySet().stream().filter(OptimizationLocalization::hasChinese).sorted(Comparator.comparingInt(String::length).reversed()).toList();
        var result=new StringBuilder();int i=0;
        while(i<source.length()){
            char c=source.charAt(i);
            if(c=='#'||source.startsWith("//",i)){
                int end=source.indexOf('\n',i);if(end<0)end=source.length();result.append(source,i,end);i=end;continue;
            }
            if(source.startsWith("/*",i)){
                int end=source.indexOf("*/",i+2);end=end<0?source.length():end+2;result.append(source,i,end);i=end;continue;
            }
            if(c=='"'||c=='\''){
                int start=i++;while(i<source.length()){char q=source.charAt(i++);if(q=='\\'&&i<source.length())i++;else if(q==c)break;}result.append(source,start,i);continue;
            }
            String match=null;
            if(i==0||!identifier(source.charAt(i-1)))for(String alias:chinese){int end=i+alias.length();if(source.startsWith(alias,i)&&(end==source.length()||!identifier(source.charAt(end)))){match=alias;break;}}
            if(match!=null){String key=aliases.get(match);if(key.isBlank())throw new IllegalArgumentException("角色名有歧义，请指定旅行者元素："+match);result.append(key);i+=match.length();continue;}
            if(Character.isLetter(c)||c=='_'){
                int start=i++;while(i<source.length()&&identifier(source.charAt(i)))i++;
                String token=source.substring(start,i);
                if(hasChinese(token))throw new IllegalArgumentException("循环中的角色名未能匹配本配队，请检查角色和元素："+token);
                result.append(token);continue;
            }
            result.append(c);i++;
        }
        return result.toString();
    }
    private static boolean identifier(char c){return Character.isLetterOrDigit(c)||c=='_';}
    private static boolean hasChinese(String text){return text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN);}
}
