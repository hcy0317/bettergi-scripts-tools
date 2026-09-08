package com.cloud_guest.artifact.optimization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationCommunityServiceTest {
    @Test void upstreamFailureHasAnActionableHttpResponse() throws Exception {
        var mapper=new ObjectMapper();var client=mock(HttpClient.class);
        var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new com.cloud_guest.controller.ArtifactCommunityController(new OptimizationCommunityService(mapper,client),mapper)).build();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/jwt/artifacts/optimizer/community/search")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
        assertEquals(502,result.getStatus());
        assertTrue(mapper.readTree(result.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("message").asText().contains("503"));
    }

    @Test void malformedResponseIsNotCachedAsAnEmptyOrSuccessfulSearch() throws Exception {
        var mapper=new ObjectMapper();var client=mock(HttpClient.class);
        var first=mock(HttpResponse.class);when(first.statusCode()).thenReturn(200);
        when(first.body()).thenReturn("{\"message\":\"not a database response\"}".getBytes(StandardCharsets.UTF_8));
        var second=mock(HttpResponse.class);when(second.statusCode()).thenReturn(200);
        when(second.body()).thenReturn("{\"data\":[]}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(first,second);
        var service=new OptimizationCommunityService(mapper,client);
        assertThrows(IllegalStateException.class,()->service.search(mapper.createObjectNode()));
        assertTrue(service.search(mapper.createObjectNode()).path("entries").isEmpty());
    }

    @Test void emptyUpstreamObjectIsASuccessfulSearchWithNoMatches() throws Exception {
        var mapper = new ObjectMapper();
        var client = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var result = new OptimizationCommunityService(mapper, client).search(mapper.createObjectNode());
        assertTrue(result.path("entries").isArray());
        assertTrue(result.path("entries").isEmpty());
        assertFalse(result.path("hasMore").asBoolean());
        assertEquals(1, result.path("page").asInt());
    }

    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="artifact.optimizer.live.community",matches="true")
    void readsMaintainedPublicDatabaseWithoutAccountData()throws Exception {
        var mapper=new ObjectMapper();var service=new OptimizationCommunityService(mapper);
        var results=service.search(mapper.readTree("{\"include\":[\"noelle\",\"furina\"],\"page\":1}"));
        assertTrue(results.path("entries").size()>0);
        var preview=service.read("https://gcsim.app/db/WpTt7J7b6DDw");
        assertTrue(preview.path("importable").asBoolean(),preview.toPrettyString());
        assertTrue(preview.path("scriptPrelude").asText().contains("pick_up_crystallize"));
    }
    @Test void publicSearchBuildsBoundedQueryWithoutForwardingAccountData()throws Exception {
        var mapper=new ObjectMapper();var client=mock(HttpClient.class);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);assertEquals("gcsim.app",request.uri().getHost());assertTrue(request.headers().firstValue("Authorization").isEmpty());
            var query=mapper.readTree(URLDecoder.decode(request.uri().getRawQuery().substring(2),StandardCharsets.UTF_8));
            assertEquals(20,query.path("limit").asInt());assertTrue(query.path("query").toString().contains("aetherdendro"));assertTrue(query.path("query").toString().contains("luminedendro"));
            var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"data\":[{\"_id\":\"WpTt7J7b6DDw\",\"config\":\"active noelle;\",\"summary\":{\"char_names\":[\"noelle\"],\"mean_dps_per_target\":1200}}]}".getBytes(StandardCharsets.UTF_8));return response;
        });
        var service=new OptimizationCommunityService(mapper,client);
        var result=service.search(mapper.readTree("{\"include\":[\"noelle\",\"aetherdendro\"],\"page\":1}"));
        assertEquals("WpTt7J7b6DDw",result.path("entries").get(0).path("id").asText());
        assertThrows(IllegalArgumentException.class,()->service.search(mapper.readTree("{\"query\":{\"$where\":\"bad\"}}")));
        assertThrows(IllegalArgumentException.class,()->service.read("https://example.com/private"));
    }
}
