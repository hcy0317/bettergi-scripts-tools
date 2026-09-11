package com.cloud_guest.artifact.optimization;

import com.cloud_guest.controller.ArtifactEngineController;
import com.cloud_guest.controller.ArtifactEngineAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OptimizationEngineErrorTest {
    @Test void expectedUpdateConflictIsNotHiddenBehindSystemBusy() throws Exception {
        var service=mock(OptimizationEngineUpdates.class);when(service.rollback(true)).thenThrow(new IllegalStateException("没有上一有效版本"));
        var mvc=MockMvcBuilders.standaloneSetup(new ArtifactEngineController(service)).setControllerAdvice(new ArtifactEngineAdvice()).build();
        mvc.perform(post("/jwt/artifacts/optimizer/engine/rollback").contentType("application/json").content("{\"confirmed\":true}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("没有上一有效版本"));
    }
}
