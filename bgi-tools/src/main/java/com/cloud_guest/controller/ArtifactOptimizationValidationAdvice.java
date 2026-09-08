package com.cloud_guest.controller;
import com.cloud_guest.artifact.optimization.OptimizationValidationException;
import com.cloud_guest.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestControllerAdvice(basePackages="com.cloud_guest.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArtifactOptimizationValidationAdvice {
    @ExceptionHandler(OptimizationValidationException.class)
    public ResponseEntity<Result<List<OptimizationValidationException.Issue>>> invalid(OptimizationValidationException error){
        var result=new Result<List<OptimizationValidationException.Issue>>().setCode(400).setMessage("请修正列出的配装输入").setData(error.issues());
        return ResponseEntity.badRequest().body(result);
    }
}
