package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipInputStream;

@Service
public class OptimizationEngineUpdates {
    private static final String REPOSITORY="https://api.github.com/repos/hcy0317/better-genshin-impact";
    private final GcsimGateway gateway;
    private final ObjectMapper mapper;
    @FunctionalInterface interface CandidateProbe { JsonNode execute(Path binary,String mode,JsonNode input,Duration timeout)throws Exception; }
    private final CandidateProbe probeProcess;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    @org.springframework.beans.factory.annotation.Autowired
    public OptimizationEngineUpdates(GcsimGateway gateway,ObjectMapper mapper){this(gateway,mapper,(binary,mode,input,timeout)->new GcsimGateway(mapper,null,binary.toString()).execute(mode,input,timeout));}
    // Replace only the external process boundary in update-transaction tests.
    OptimizationEngineUpdates(GcsimGateway gateway,ObjectMapper mapper,CandidateProbe probeProcess){this.gateway=gateway;this.mapper=mapper;this.probeProcess=probeProcess;}
    private static String platform(){return System.getProperty("os.name").startsWith("Windows")?"windows":"linux";}
    public ObjectNode check()throws Exception{
        var result=mapper.createObjectNode();
        try{result.set("active",gateway.catalog());}catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}catch(Exception e){result.put("activeError",e.getMessage());}
        try {
            var repository=fetchJson("https://api.github.com/repos/genshinsim/gcsim");
            String branch=repository.path("default_branch").asText();
            if(branch.isBlank()||branch.length()>255)throw new IllegalStateException("上游未返回有效默认分支");
            var latest=fetchJson("https://api.github.com/repos/genshinsim/gcsim/commits/"+java.net.URLEncoder.encode(branch,StandardCharsets.UTF_8));
            String revision=latest.path("sha").asText();if(!revision.matches("[0-9a-f]{40}"))throw new IllegalStateException("上游提交信息不完整");
            result.put("upstreamRevision",revision).put("upstreamBranch",branch);
        } catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}
        catch(Exception error){result.put("upstreamError","无法检查上游："+Objects.toString(error.getMessage(),error.getClass().getSimpleName()));}
        var packages=result.putArray("packages");
        try {
        var releases=fetchJson(REPOSITORY+"/releases?per_page=30");
        if(!releases.isArray())throw new IllegalStateException("发布包列表格式无效");
        for(JsonNode release:releases){
            if(!release.path("tag_name").asText().startsWith("gcsim-engine-"))continue;
            for(JsonNode asset:release.path("assets"))if(asset.path("name").asText().equals("gcsim-bridge-"+platform()+"-amd64.zip")&&asset.path("digest").asText().matches("sha256:[0-9a-f]{64}")){
                packages.addObject().put("assetId",asset.path("id").asLong()).put("name",asset.path("name").asText()).put("release",release.path("tag_name").asText()).put("digest",asset.path("digest").asText());
            }
        }
        } catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}
        catch(Exception error){result.put("packagesError","无法检查验证包："+Objects.toString(error.getMessage(),error.getClass().getSimpleName()));}
        result.put("checkStatus",result.has("activeError")||result.has("upstreamError")||result.has("packagesError")?"partial":"complete");
        result.put("note","动态机制来自固定版本 gcsim。上游有新提交但未发布验证包时，可运行仓库的 gcsim packages 工作流；不需要手写全量角色数据库。");return result;
    }
    public ObjectNode install(long assetId,boolean confirmed)throws Exception{
        if(!confirmed)throw new IllegalArgumentException("必须确认验证并切换引擎");
        var metadata=fetchJson(REPOSITORY+"/releases/assets/"+assetId);
        String name=metadata.path("name").asText(),expected=metadata.path("digest").asText(),url=metadata.path("browser_download_url").asText();
        if(!name.equals("gcsim-bridge-"+platform()+"-amd64.zip")||!expected.matches("sha256:[0-9a-f]{64}")||!url.startsWith("https://github.com/hcy0317/better-genshin-impact/releases/download/gcsim-engine-"))throw new IllegalArgumentException("仅接受本仓库验证引擎发布包及 GitHub 提供的摘要");
        byte[] archive=fetch(url,64*1024*1024);if(!digest(archive).equals(expected.substring(7)))throw new IllegalArgumentException("下载包校验失败，保留旧有效版本");
        return gateway.whileIdle(()->stageAndActivate(archive));
    }
    // Package-private seam for tests using a self-built trusted fixture. Production
    // only calls this after validating the fixed-repository GitHub asset digest.
    ObjectNode stageAndActivate(byte[] archive)throws Exception{
        Path directory=gateway.directory();Files.createDirectories(directory.resolve("versions"));
        Path stage=Files.createTempDirectory(directory.resolve("versions"),"candidate-");String binaryName=platform().equals("windows")?"gcsim-bridge.exe":"gcsim-bridge";
        var names=new HashSet<String>();long total=0;
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
            String name=entry.getName();if(entry.isDirectory()||!Set.of("manifest.json",binaryName,"LICENSE","GCSIM-LICENSE").contains(name)||!names.add(name))throw new IllegalArgumentException("更新包含未知路径、重复文件或目录");
            byte[] bytes=zip.readNBytes(128*1024*1024+1);total+=bytes.length;if(total>128*1024*1024)throw new IllegalArgumentException("解压数据超过限制");Files.write(stage.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
        }}
        if(!names.containsAll(Set.of("manifest.json",binaryName)))throw new IllegalArgumentException("更新包不完整");
        var manifest=mapper.readTree(stage.resolve("manifest.json").toFile());String revision=manifest.path("engineRevision").asText();Path binary=stage.resolve(binaryName);
        if(manifest.path("schemaVersion").asInt()!=1||!revision.matches("[0-9a-f]{40}")||!manifest.path("platform").asText().equals(platform())||!manifest.path("architecture").asText().equals("amd64")||!manifest.path("executable").asText().equals(binaryName)||!digest(Files.readAllBytes(binary)).equals(manifest.path("sha256").asText()))throw new IllegalArgumentException("更新包架构、身份或程序摘要无效");
        if(platform().equals("linux"))Files.setPosixFilePermissions(binary,Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        var capabilities=probeProcess.execute(binary,"--capabilities",null,Duration.ofSeconds(20));
        if(!revision.equals(capabilities.path("engineRevision").asText())||!manifest.path("sdkVersion").asText().equals(capabilities.path("sdk").path("version").asText())||!manifest.path("adapterVersion").asText().equals(capabilities.path("adapterVersion").asText()))throw new IllegalStateException("引擎、适配器与数据版本不一致，未激活");
        var catalog=probeProcess.execute(binary,"--catalog",null,Duration.ofSeconds(20));
        if(catalog==null||!revision.equals(catalog.path("engineRevision").asText()))throw new IllegalStateException("引擎目录版本不一致，未激活");
        for(String field:List.of("characters","weapons","sets")){
            var entries=catalog.path(field);var keys=new HashSet<String>();
            if(!entries.isArray()||entries.isEmpty())throw new IllegalStateException("引擎目录缺少有效的 "+field+"，未激活");
            for(JsonNode entry:entries)if(!entry.isObject()||entry.path("key").asText().isBlank()||!keys.add(entry.path("key").asText()))throw new IllegalStateException("引擎目录条目标识无效或重复："+field+"，未激活");
        }
        var result=probeProcess.execute(binary,"--optimize",probe(),Duration.ofSeconds(25));
        if(!result.path("result").path("plan").path("qualified").asBoolean()||result.path("result").path("plan").path("rank").path("weightedDps").asDouble()<=0)throw new IllegalStateException("代表场景回归未通过，未激活");
        var active=mapper.createObjectNode().put("engineRevision",revision).put("executable",directory.relativize(binary).toString().replace('\\','/')).put("validatedAt",java.time.Instant.now().toString());
        Path pointer=directory.resolve("active.json");if(Files.isRegularFile(pointer))atomicWrite(directory.resolve("previous.json"),Files.readAllBytes(pointer));
        else{
            Path previousBinary=null;try{previousBinary=gateway.executable();}catch(IllegalStateException unavailable){/* First installation has no previous program. */}
            if(previousBinary!=null&&Files.isRegularFile(previousBinary)){
                Path saved=Files.createTempDirectory(directory.resolve("versions"),"previous-").resolve(binaryName);Files.copy(previousBinary,saved);
                var previousCap=new GcsimGateway(mapper,null,saved.toString()).execute("--capabilities",null,Duration.ofSeconds(20));
                var previous=mapper.createObjectNode().put("engineRevision",previousCap.path("engineRevision").asText()).put("executable",directory.relativize(saved).toString().replace('\\','/'));
                atomicWrite(directory.resolve("previous.json"),mapper.writeValueAsBytes(previous));
            }
        }
        atomicWrite(pointer,mapper.writeValueAsBytes(active));return active.put("manualBuffReviewRequired",true).put("note","新引擎已通过回归并激活；角色档案、手工 Buff 和旧有效程序均保留。额外 Buff 需按新版本复核。");
    }
    public ObjectNode rollback(boolean confirmed)throws Exception{
        if(!confirmed)throw new IllegalArgumentException("必须确认回退引擎");return gateway.whileIdle(()->{Path directory=gateway.directory(),previous=directory.resolve("previous.json"),active=directory.resolve("active.json");if(!Files.isRegularFile(previous))throw new IllegalStateException("没有上一有效版本");byte[] old=Files.readAllBytes(previous);JsonNode value=mapper.readTree(old);Path executable=directory.resolve(value.path("executable").asText()).normalize();if(!executable.startsWith(directory.resolve("versions"))||!Files.isRegularFile(executable)||Files.isSymbolicLink(executable))throw new IllegalStateException("旧版本已不可用");byte[] current=Files.readAllBytes(active);atomicWrite(active,old);atomicWrite(previous,current);return ((ObjectNode)value).put("note","已回退程序与对应数据；个人档案和手动补充未被覆盖");});
    }
    private ObjectNode probe()throws Exception{
        var request=(ObjectNode)mapper.readTree("""
                {"schemaVersion":"1","mode":"balanced","inventory":{"uid":"package-probe","scanSessionId":"probe","catalogVersion":"probe","snapshotDigest":"probe"},"items":[],"characters":[{"character":"amber","weight":1}],"scenarios":[{"id":"smoke","weight":1,"evaluation":{"config":"options duration=6; target lvl=90 resist=0.1; amber char lvl=90/90 cons=0 talent=6,6,6; amber add weapon=\\\"huntersbow\\\" refine=1 lvl=90/90; active amber; while 1 { amber attack; }"}}],"searchSeeds":[17],"validationSeeds":[29,31],"evaluationBudget":16,"exact":true}
                """);
        String[] slots={"flower","plume","sands","goblet","circlet"},keys={"hp","atk","atk_","pyro_dmg_","critRate_"};double[] values={4780,311,46.6,46.6,31.1};var items=(com.fasterxml.jackson.databind.node.ArrayNode)request.path("items");for(int i=0;i<5;i++)items.addObject().put("scanIndex",i).put("slotKey",slots[i]).put("setKey","EmblemOfSeveredFate").put("mainStatKey",keys[i]).put("mainStatValue",values[i]);
        var job=mapper.createObjectNode();job.set("optimization",request);job.putObject("limits").put("wallTimeMs",20000).put("memoryMiB",512);return job;
    }
    protected JsonNode fetchJson(String url)throws Exception{return mapper.readTree(fetch(url,4*1024*1024));}
    private byte[] fetch(String url,int limit)throws Exception{var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45)).header("User-Agent","BetterGI-Gcsim-Updates").GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(var body=response.body()){if(response.statusCode()!=200)throw new IllegalStateException("更新源返回 "+response.statusCode()+"；旧有效版本保持不变");byte[] bytes=body.readNBytes(limit+1);if(bytes.length>limit)throw new IllegalArgumentException("更新响应超过大小限制");return bytes;}}
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void atomicWrite(Path target,byte[] bytes)throws Exception{Path temporary=Files.createTempFile(target.getParent(),"pointer-",".tmp");Files.write(temporary,bytes);try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){throw new IllegalStateException("版本指针不支持原子切换，未更改有效版本",e);}}
}
