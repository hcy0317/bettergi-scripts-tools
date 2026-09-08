package com.cloud_guest.artifact.optimization;

import java.util.*;

/** Lexical boundaries only. gcsim remains the authority for executable syntax. */
public final class OptimizationScriptParts {
    public record Statement(String raw,String code,int line){}
    private OptimizationScriptParts(){}
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
}
