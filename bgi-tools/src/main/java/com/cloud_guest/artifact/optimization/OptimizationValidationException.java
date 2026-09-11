package com.cloud_guest.artifact.optimization;
import java.util.List;

public class OptimizationValidationException extends IllegalArgumentException {
    public record Issue(String scope,String character,String buildId,String field,String message,Integer startOffset,Integer endOffset,Integer line,Integer column){
        public Issue(String scope,String character,String buildId,String field,String message){this(scope,character,buildId,field,message,null,null,null,null);}
    }
    private final List<Issue> issues;
    public OptimizationValidationException(List<Issue> issues){super(issues.isEmpty()?"请检查配装输入":issues.get(0).message());this.issues=List.copyOf(issues);}
    public List<Issue> issues(){return issues;}
    public static OptimizationValidationException scene(String id,String field,String message){return new OptimizationValidationException(List.of(new Issue("build","",id,field,message)));}
}
