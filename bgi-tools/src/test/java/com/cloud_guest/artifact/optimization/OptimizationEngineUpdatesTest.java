package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OptimizationEngineUpdatesTest {
    @TempDir Path directory;
    private byte[] archive(Map<String,byte[]> files)throws Exception{var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return out.toByteArray();}
    @Test void badPackageCannotChangeTheActivePointer()throws Exception{
        var mapper=new ObjectMapper();var gateway=new GcsimGateway(mapper,null,directory.resolve("gcsim-bridge.exe").toString());
        Path active=directory.resolve("active.json");Files.writeString(active,"old-valid-pointer");
        var updater=new OptimizationEngineUpdates(gateway,mapper);
        assertThrows(IllegalArgumentException.class,()->updater.stageAndActivate(archive(Map.of("../escape",new byte[]{1}))));
        assertEquals("old-valid-pointer",Files.readString(active));
        assertThrows(IllegalArgumentException.class,()->updater.rollback(false));
    }
    @Test void unusableCatalogCannotReplaceThePreviousEngineEvenWhenDpsProbeWouldPass()throws Exception{
        var mapper=new ObjectMapper();String revision="1234567890123456789012345678901234567890";
        String platform=System.getProperty("os.name").startsWith("Windows")?"windows":"linux";
        String binaryName=platform.equals("windows")?"gcsim-bridge.exe":"gcsim-bridge";byte[] binary={1};
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(binary));
        var manifest=mapper.createObjectNode().put("schemaVersion",1).put("engineRevision",revision).put("platform",platform).put("architecture","amd64").put("executable",binaryName).put("sha256",hash).put("sdkVersion","test-sdk");
        Path active=directory.resolve("active.json");Files.writeString(active,"old-valid-pointer");
        var gateway=new GcsimGateway(mapper,null,directory.resolve(binaryName).toString());
        for(String badCatalog:List.of("{}","{\"characters\":[]}","{\"characters\":[{\"key\":\"amber\"}],\"engineRevision\":\"wrong\"}")){
            var updater=new OptimizationEngineUpdates(gateway,mapper,(executable,mode,request,timeout)->{
                if(mode.equals("--capabilities"))return mapper.readTree("{\"engineRevision\":\""+revision+"\",\"sdk\":{\"version\":\"test-sdk\"}}");
                if(mode.equals("--catalog"))return mapper.readTree(badCatalog);
                if(mode.equals("--optimize"))return mapper.readTree("{\"result\":{\"plan\":{\"qualified\":true,\"rank\":{\"weightedDps\":1}}}}");
                throw new AssertionError(mode);
            });
            assertThrows(IllegalStateException.class,()->updater.stageAndActivate(archive(Map.of("manifest.json",mapper.writeValueAsBytes(manifest),binaryName,binary))));
            assertEquals("old-valid-pointer",Files.readString(active));
        }
    }
    @Test void selfBuiltPackagePassesRealRegressionBeforeActivation()throws Exception{
        String executable=System.getProperty("artifact.optimizer.test.executable","");assumeTrue(!executable.isBlank());
        var mapper=new ObjectMapper();byte[] binary=Files.readAllBytes(Path.of(executable));
        var source=new GcsimGateway(mapper,null,executable);var cap=source.execute("--capabilities",null,java.time.Duration.ofSeconds(20));
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(binary));
        var manifest=mapper.createObjectNode().put("schemaVersion",1).put("engineRevision",cap.path("engineRevision").asText()).put("platform","windows").put("architecture","amd64").put("executable","gcsim-bridge.exe").put("sha256",hash).put("sdkVersion",cap.path("sdk").path("version").asText());
        var gateway=new GcsimGateway(mapper,null,directory.resolve("gcsim-bridge.exe").toString());var updater=new OptimizationEngineUpdates(gateway,mapper);
        var active=updater.stageAndActivate(archive(Map.of("manifest.json",mapper.writeValueAsBytes(manifest),"gcsim-bridge.exe",binary)));
        assertEquals(cap.path("engineRevision").asText(),active.path("engineRevision").asText());
        assertTrue(gateway.executable().startsWith(directory.resolve("versions")));
        assertTrue(Files.isRegularFile(directory.resolve("active.json")));
        String first=gateway.executable().toString();
        updater.stageAndActivate(archive(Map.of("manifest.json",mapper.writeValueAsBytes(manifest),"gcsim-bridge.exe",binary)));
        assertNotEquals(first,gateway.executable().toString());
        updater.rollback(true);
        assertEquals(first,gateway.executable().toString());
    }
}
