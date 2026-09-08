package com.cloud_guest.artifact.optimization;

import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;

@Component
public class GcsimGateway {
    private final ObjectMapper mapper;
    private final CultivationMaterialSourceCatalog sources;
    private final String configuredPath;
    private final Semaphore processSlot = new Semaphore(1);
    private volatile JsonNode catalog;
    public GcsimGateway(ObjectMapper mapper,CultivationMaterialSourceCatalog sources,
                        @Value("${artifact.optimizer.executable:}") String configuredPath) {
        this.mapper=mapper;this.sources=sources;this.configuredPath=configuredPath;
    }
    public Path executable() {
        var path=baseExecutable();
        Path active=directory().resolve("active.json");
        if(Files.isRegularFile(active)&&!Files.isSymbolicLink(active)){
            try{if(Files.size(active)>8192)throw new IllegalStateException("引擎版本指针过大");String relative=mapper.readTree(active.toFile()).path("executable").asText();Path managed=directory().resolve(relative).normalize();if(!managed.startsWith(directory().resolve("versions"))||Files.isSymbolicLink(managed))throw new IllegalStateException("引擎版本指针越界");path=managed;}catch(java.io.IOException e){throw new IllegalStateException("无法读取有效引擎版本",e);}
        }
        if(!path.isAbsolute()||!Files.isRegularFile(path)||Files.isSymbolicLink(path)) throw new IllegalStateException("尚未配置 gcsim 计算程序，请设置 artifact.optimizer.executable 为已构建程序的绝对路径");
        return path;
    }
    private Path baseExecutable(){return configuredPath.isBlank()?sources.betterGiRoot().resolve("Lib/gcsim/gcsim-bridge"+(System.getProperty("os.name").startsWith("Windows")?".exe":"")):Path.of(configuredPath);}
    Path directory(){Path path=baseExecutable();if(!path.isAbsolute())throw new IllegalStateException("计算程序必须使用绝对路径");return path.getParent().normalize();}
    <T> T whileIdle(java.util.concurrent.Callable<T> action)throws Exception{if(!processSlot.tryAcquire())throw new IllegalStateException("计算正在运行，不能切换引擎");try{T result=action.call();invalidateCatalog();return result;}finally{processSlot.release();}}
    public JsonNode catalog() throws Exception {
        var value=catalog;
        if(value!=null)return value.deepCopy();
        value=execute("--catalog",null,Duration.ofSeconds(20));
        if(!value.path("characters").isArray()||value.path("engineRevision").asText().length()!=40)throw new IllegalStateException("gcsim 目录不完整");
        if(sources!=null){
            try{
                Path nativeFile=sources.betterGiRoot().resolve(Path.of("GameTask","AutoFight","Assets","combat_avatar.json"));
                if(Files.isRegularFile(nativeFile)&&Files.size(nativeFile)<=2_000_000){
                    JsonNode nativeData=mapper.readTree(nativeFile.toFile());
                    value=OptimizationInventoryCatalog.attach(value,nativeData);
                }else throw new IllegalStateException("角色身份目录不存在或超过大小限制");
            }catch(Exception error){((com.fasterxml.jackson.databind.node.ObjectNode)value).put("nativeAliasWarning","BetterGI 完整角色身份目录暂不可用；无法核实的穿戴者仍需修正，不会绕过装备保护");}
        }
        catalog=value;return value.deepCopy();
    }
    public void invalidateCatalog(){catalog=null;}
    public JsonNode execute(String mode,JsonNode request,Duration timeout) throws Exception {
        if(!Set.of("--catalog","--capabilities","--optimize","--rotation").contains(mode)||timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofSeconds(130))>0)throw new IllegalArgumentException("无效的计算操作或时限");
        byte[] input=request==null?new byte[0]:mapper.writeValueAsBytes(request);
        if(input.length>2*1024*1024)throw new IllegalArgumentException("计算请求超过 2 MiB，请缩小场景数量");
        if(!processSlot.tryAcquire())throw new IllegalStateException("已有计算正在运行，请等待或取消后重试");
        Process process=null;
        try(var io=Executors.newVirtualThreadPerTaskExecutor()) {
            process=new ProcessBuilder(executable().toString(),mode).start();
            var owned=process;
            Future<byte[]> out=io.submit(()->readBounded(owned.getInputStream(),16*1024*1024,owned));
            Future<byte[]> error=io.submit(()->readBounded(owned.getErrorStream(),64*1024,owned));
            Future<?> writer=io.submit(()->{try(var stream=owned.getOutputStream()){stream.write(input);}return null;});
            try {
                if(!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))throw new TimeoutException("gcsim 计算达到墙钟时限");
                writer.get(2,TimeUnit.SECONDS);
                byte[] output=out.get(2,TimeUnit.SECONDS),errors=error.get(2,TimeUnit.SECONDS);
                JsonNode response=output.length==0?mapper.createObjectNode():mapper.readTree(output);
                if(process.exitValue()!=0)throw new IllegalStateException(response.path("error").asText("gcsim 计算失败，退出码 "+process.exitValue())+(errors.length>0?"；计算程序已退出":""));
                return response;
            } finally {
                if(process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
                process.getInputStream().close();process.getErrorStream().close();process.getOutputStream().close();
                writer.cancel(true);out.cancel(true);error.cancel(true);
            }
        } finally { if(process!=null&&process.isAlive())process.destroyForcibly();processSlot.release(); }
    }
    private static byte[] readBounded(InputStream stream,int limit,Process process) throws Exception {
        try(stream){var bytes=stream.readNBytes(limit+1);if(bytes.length>limit){process.destroyForcibly();throw new IllegalStateException("计算输出超过大小限制");}return bytes;}
    }
}
