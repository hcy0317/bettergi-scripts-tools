package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OptimizationEnkaTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void importsFromCanonicalEnkaEndpoint() throws Exception {
        var client=mock(HttpClient.class);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{
            HttpRequest request=invocation.getArgument(0);
            boolean canonical=request.uri().getPath().equals("/api/uid/102550550");
            var response=mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(canonical?200:308);
            when(response.headers()).thenReturn(HttpHeaders.of(canonical?Map.of():Map.of("Location",List.of("/api/uid/102550550")),(a,b)->true));
            when(response.body()).thenReturn(new ByteArrayInputStream((canonical?"{\"avatarInfoList\":[{\"avatarId\":10000021,\"skillLevelMap\":{\"10211\":6,\"10212\":8,\"10215\":9}}],\"ttl\":60}":"").getBytes(StandardCharsets.UTF_8)));
            return response;
        });
        var catalog=mapper.readTree("{\"characters\":[{\"id\":10000021,\"key\":\"amber\",\"skill_details\":{\"attack\":10211,\"skill\":10212,\"burst\":10215}}]}");
        var result=new OptimizationEnka(mapper,client).preview("102550550",catalog);
        assertEquals("amber",result.path("characters").get(0).path("key").asText());
        assertEquals(8,result.path("characters").get(0).path("talents").get(1).asInt());
    }
}
