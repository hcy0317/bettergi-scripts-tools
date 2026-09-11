package com.cloud_guest.controller;

import com.cloud_guest.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=ArtifactEngineController.class)
public class ArtifactEngineAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException error){return reply(400,error.getMessage());}
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> conflict(IllegalStateException error){return reply(409,error.getMessage());}
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Result<Void>> unavailable(IOException error){return reply(502,"引擎更新读写或网络请求失败，请检查当前版本状态后重试");}
    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<Result<Void>> interrupted(InterruptedException error){Thread.currentThread().interrupt();return reply(409,"引擎维护操作已取消");}
    private static ResponseEntity<Result<Void>> reply(int code,String message){return ResponseEntity.status(code).body(new Result<Void>().setCode(code).setMessage(message));}
}
