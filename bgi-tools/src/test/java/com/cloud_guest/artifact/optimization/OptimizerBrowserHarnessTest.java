package com.cloud_guest.artifact.optimization;

import com.cloud_guest.artifact.domain.*;
import com.cloud_guest.artifact.job.*;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.cloud_guest.controller.ArtifactOptimizationController;
import com.cloud_guest.controller.ArtifactRotationController;
import com.cloud_guest.controller.ArtifactEquipmentController;
import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in browser harness. Real controllers/compiler/worker, synthetic in-memory
 * account and repository boundary. Never starts Spring schedulers or reads runtime data. */
class OptimizerBrowserHarnessTest {
    @TempDir Path launchRoot;
    @Test void serveIsolatedWorkbenchForBrowserVerification() throws Exception {
        int port=Integer.getInteger("artifact.optimizer.browser.port",0);String exe=System.getProperty("artifact.optimizer.test.executable","");
        assumeTrue(port>0&&!exe.isBlank(),"explicit browser harness port and built engine required");
        var mapper=new ObjectMapper();var memory=new ConcurrentHashMap<String,ObjectNode>();var store=mock(ArtifactJsonStore.class);
        when(store.get(anyString(),anyString(),eq(ObjectNode.class))).thenAnswer(i->Optional.ofNullable(memory.get(i.getArgument(0)+"|"+i.getArgument(1))).map(ObjectNode::deepCopy));
        when(store.put(anyString(),anyString(),any(ObjectNode.class))).thenAnswer(i->{ObjectNode v=i.getArgument(2);memory.put(i.getArgument(0)+"|"+i.getArgument(1),v.deepCopy());return v;});
        when(store.listByKeyPrefixLimited(anyString(),anyString(),eq(ObjectNode.class),anyInt())).thenAnswer(i->memory.entrySet().stream().filter(e->e.getKey().startsWith(i.getArgument(0)+"|"+i.getArgument(1))).map(e->e.getValue().deepCopy()).toList());
        var scans=mock(ArtifactAnalysisJobRepository.class);
        String[] slots={"flower","plume","sands","goblet","circlet"},keys={"hp","atk","atk_","pyro_dmg_","critRate_"};var items=new ArrayList<ArtifactItem>();
        for(int n=0;n<2;n++)for(int s=0;s<5;s++)items.add(new ArtifactItem(n*5+s,"EmblemOfSeveredFate",slots[s],20,5,keys[s],List.of(new ArtifactSubstat("critDMG_",14+7*n,false)),n==0?"安柏":"凯亚",false));
        var scan=new ArtifactAnalysisJob("browser-scan","100000001",ArtifactLaunchOperation.ANALYZE,ArtifactAnalysisJobStatus.READY_FOR_REVIEW,ArtifactSnapshot.create("100000001","browser-scan","default","synthetic-fixture",items),null,null,"2026-09-07T00:00:00Z","2026-09-07T00:00:00Z",null);
        when(scans.findById("browser-scan")).thenReturn(Optional.of(scan));when(scans.findByUid("100000001")).thenReturn(List.of(scan));
        var sources=mock(CultivationMaterialSourceCatalog.class);
        when(sources.betterGiRoot()).thenReturn(Path.of(System.getProperty("artifact.optimizer.test.bettergiRoot")));
        var workspace=new OptimizationWorkspace(store,mapper);var gateway=new GcsimGateway(mapper,sources,exe);var mainstats=new OptimizationMainStats(mapper);var jobs=new OptimizationJobs(store,scans,workspace,mainstats,gateway,mapper);
        var controller=new ArtifactOptimizationController(workspace,jobs,gateway,new OptimizationEnka(mapper),scans);
        var strategies=new OptimizationStrategies(sources,mapper);
        var rotations=new ArtifactRotationController(new OptimizationRotationService(workspace,scans,mainstats,gateway,jobs,strategies,mapper),strategies);
        var equipment=new ArtifactEquipmentController(new OptimizationEquipmentPlans(store,jobs,workspace,scans,new com.cloud_guest.artifact.launch.ArtifactLaunchRequestService(launchRoot,mapper,java.time.Clock.systemUTC(),java.time.Duration.ofMinutes(10)),gateway,mapper));
        workspace.save("100000001",(ObjectNode)mapper.readTree("""
                {"version":0,"characters":[
                {"key":"amber","name":"安柏（测试）","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"huntersbow","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":1,"tags":["合成夹具"],"protected":false,"minimumStats":{},"mainStats":{},"requiredSets":{},"fixedSlots":{},"builds":[{"id":"fixture-team","weight":1,"metric":"damage_per_round","reference":0}]},
                {"key":"kaeya","name":"凯亚（测试）","level":90,"maxLevel":90,"constellation":0,"talents":[6,6,6],"weapon":"dullblade","weaponLevel":90,"weaponMaxLevel":90,"refinement":1,"weight":1,"tags":["合成夹具"],"protected":false,"minimumStats":{},"mainStats":{},"requiredSets":{},"fixedSlots":{},"builds":[{"id":"fixture-team","weight":1,"metric":"damage_per_round","reference":0}]}],
                "builds":[{"id":"fixture-team","name":"安柏与凯亚（离线夹具）","weight":1,"duration":10,"enemyLevel":90,"resistance":0.1,"enemyCount":1,"members":[{"character":"amber","kind":"real_fixed"},{"character":"kaeya","kind":"real_fixed"}],"rotation":"active amber; while 1 { amber attack:3; kaeya attack:3; }","rounds":[],"constraints":[],"buffs":[],"allowPartial":false}]}
                """));
        var done=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);var requests=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(requests);
        Path dist=Path.of("../frontend/dist").toAbsolutePath().normalize();if(!Files.exists(dist))dist=Path.of("frontend/dist").toAbsolutePath().normalize();final Path assets=dist;
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();Object result=null;
                var params=new HashMap<String,String>();String query=exchange.getRequestURI().getRawQuery();if(query!=null)for(String pair:query.split("&")){String[] part=pair.split("=",2);params.put(URLDecoder.decode(part[0],StandardCharsets.UTF_8),part.length>1?URLDecoder.decode(part[1],StandardCharsets.UTF_8):"");}
                String uid=params.getOrDefault("uid","100000001");String prefix="/bgi/jwt/artifacts/optimizer";
                if(path.equals("/test/done")){done.countDown();result=Map.of("code",200,"data",true);}
                else if(path.equals("/bgi/jwt/uid/selection/all"))result=Map.of("code",200,"data",List.of(Map.of("uid","100000001","as","离线合成账号","defaultUid",true)));
                else if(path.equals(prefix+"/catalog"))result=controller.catalog();
                else if(path.equals(prefix+"/workspace"))result=method.equals("PUT")?controller.save(uid,(ObjectNode)mapper.readTree(exchange.getRequestBody())):controller.workspace(uid);
                else if(path.equals(prefix+"/snapshots"))result=controller.snapshots(uid);
                else if(path.startsWith(prefix+"/snapshots/"))result=controller.snapshot(uid,path.substring((prefix+"/snapshots/").length()));
                else if(path.equals(prefix+"/jobs"))result=method.equals("POST")?controller.start(uid,(ObjectNode)mapper.readTree(exchange.getRequestBody())):controller.jobs(uid);
                else if(path.equals(prefix+"/rotations/jobs"))result=rotations.start(uid,(ObjectNode)mapper.readTree(exchange.getRequestBody()));
                else if(path.equals(prefix+"/rotations/import"))result=rotations.load(uid,(ObjectNode)mapper.readTree(exchange.getRequestBody()));
                else if(path.equals(prefix+"/rotations/strategies"))result=rotations.strategies();
                else if(path.startsWith(prefix+"/rotations/jobs/")&&path.endsWith("/preview")){String id=path.substring((prefix+"/rotations/jobs/").length()).replace("/preview","");result=rotations.preview(uid,id,params.get("preset"));}
                else if(path.equals(prefix+"/equipment/preview"))result=equipment.preview(uid,(ObjectNode)mapper.readTree(exchange.getRequestBody()));
                else if(path.startsWith(prefix+"/jobs/")){String[] parts=path.substring((prefix+"/jobs/").length()).split("/");result=parts.length==2?controller.cancel(uid,parts[0]):controller.job(uid,parts[0]);}
                else if(path.startsWith("/bgi/")&&!path.startsWith("/bgi/ui/"))result=Map.of("code",200,"data","offline-test");
                if(result!=null){byte[] bytes=mapper.writeValueAsBytes(result);exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}
                else{
                    String relative=path.startsWith("/bgi/ui/")?path.substring(8):path.substring(1);Path file=assets.resolve(relative).normalize();if(!file.startsWith(assets))throw new IllegalArgumentException("invalid path");if(!Files.isRegularFile(file))file=assets.resolve("index.html");byte[] bytes=Files.readAllBytes(file);String type=file.toString().endsWith(".js")?"text/javascript":file.toString().endsWith(".css")?"text/css":file.toString().endsWith(".html")?"text/html":"application/octet-stream";
                    if(type.equals("text/html"))bytes=new String(bytes,StandardCharsets.UTF_8).replace("<head>","<head><script>localStorage.setItem('bgi_tools_token','synthetic-browser-test');localStorage.setItem('satoken','synthetic-browser-test');</script>").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
                }
            }catch(Exception error){byte[] bytes=mapper.writeValueAsBytes(Map.of("code",500,"message",Objects.toString(error.getMessage(),"error")));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });
        try {server.start();System.out.println("OPTIMIZER_BROWSER_HARNESS_READY http://127.0.0.1:"+port+"/bgi/ui/Artifacts/Optimizer (synthetic account, real engine)");done.await(20,TimeUnit.MINUTES);}
        finally{server.stop(0);jobs.close();requests.shutdownNow();}
    }
}
