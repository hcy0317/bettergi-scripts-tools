package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A real child process exercises pipe limits without depending on simulator timing. */
class GcsimGatewayOutputTest {
    @TempDir static Path directory;
    static Path binary;
    @BeforeAll static void buildProcessFixture()throws Exception {
        Process available;
        try {available=new ProcessBuilder("go","version").redirectErrorStream(true).start();}
        catch(java.io.IOException unavailable){assumeTrue(false,"Go is required for the real process fixture");return;}
        try {assumeTrue(available.waitFor(10,TimeUnit.SECONDS)&&available.exitValue()==0,"Go toolchain unavailable");}
        finally {if(available.isAlive())available.destroyForcibly();}
        var source=directory.resolve("output.go");
        Files.writeString(source,"""
            package main
            import ("os";"strings";"io";"bytes")
            func main(){
              io.Copy(io.Discard,os.Stdin)
              name,_:=os.Executable()
              size:=17*1024*1024
              if strings.Contains(name,"boundary"){size=64*1024*1024}
              if strings.Contains(name,"oversize"){size=64*1024*1024+1}
              document:=[]byte(`{"status":"completed","result":{"qualified":true,"tail":"intact"}}`)
              block:=bytes.Repeat([]byte(" "),1024)
              for remaining:=size-len(document);remaining>0; {n:=min(remaining,len(block));os.Stdout.Write(block[:n]);remaining-=n}
              os.Stdout.Write(document)
            }
            """);
        binary=directory.resolve("large.exe");
        var build=new ProcessBuilder("go","build","-o",binary.toString(),source.toString()).redirectErrorStream(true).start();
        try {assertTrue(build.waitFor(45,TimeUnit.SECONDS),"fixture build timeout");assertEquals(0,build.exitValue(),new String(build.getInputStream().readAllBytes()));}
        finally {if(build.isAlive())build.destroyForcibly();}
    }
    @Test void largeAndExactBoundaryOutputsRemainComplete()throws Exception {
        for(String name:new String[]{"large.exe","boundary.exe"}){
            var executable=directory.resolve(name);if(!executable.equals(binary))Files.copy(binary,executable);
            var m=new ObjectMapper();var payload=m.createObjectNode();payload.putObject("limits").put("outputKiB",65536);
            var result=new GcsimGateway(m,null,executable.toString()).execute("--optimize",payload,Duration.ofSeconds(20));
            assertEquals("completed",result.path("status").asText());
            assertTrue(result.path("result").path("qualified").asBoolean());
            assertEquals("intact",result.path("result").path("tail").asText());
        }
    }
    @Test void arbitraryRequestCannotRemoveTheHardOutputCeiling()throws Exception {
        var executable=directory.resolve("oversize.exe");Files.copy(binary,executable);
        var m=new ObjectMapper();var payload=m.createObjectNode();payload.putObject("limits").put("outputKiB",Integer.MAX_VALUE);
        var error=assertThrows(Exception.class,()->new GcsimGateway(m,null,executable.toString()).execute("--rotation",payload,Duration.ofSeconds(20)));
        while(error.getCause()!=null)error=(Exception)error.getCause();
        assertEquals("计算输出超过大小限制",error.getMessage());
    }
}
