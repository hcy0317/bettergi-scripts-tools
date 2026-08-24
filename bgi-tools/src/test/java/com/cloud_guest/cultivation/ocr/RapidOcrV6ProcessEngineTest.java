package com.cloud_guest.cultivation.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RapidOcrV6ProcessEngineTest {

    @TempDir
    Path tempDirectory;

    @Test
    void decodesStructuredV6OutputAndPreservesModelProvenance() throws Exception {
        CultivationOcrProcess process = mock(CultivationOcrProcess.class);
        when(process.run(any(), any())).thenReturn(new CultivationOcrProcessResult(0, """
                {
                  "engineVersion": "RapidOCR 3.9.2 / PP-OCRv6",
                  "modelSource": "bettergi",
                  "imageWidth": 1280,
                  "imageHeight": 10892,
                  "blocks": [{
                    "text": "摩拉",
                    "confidence": 0.99,
                    "polygon": [
                      {"x": 120.0, "y": 150.0},
                      {"x": 220.0, "y": 150.0},
                      {"x": 220.0, "y": 180.0},
                      {"x": 120.0, "y": 180.0}
                    ]
                  }]
                }
                """, "RapidOCR initialized"));

        CultivationOcrProperties properties = new CultivationOcrProperties();
        properties.setPythonCommand("python-test");
        properties.setTimeout(Duration.ofSeconds(12));
        RapidOcrV6ProcessEngine engine = new RapidOcrV6ProcessEngine(
                process, new ObjectMapper(), properties,
                tempDirectory.resolve("rapidocr_v6_bridge.py"), tempDirectory);

        CultivationOcrResult result = engine.recognize(tempDirectory.resolve("calculator.png"));

        assertThat(result.engineVersion()).contains("PP-OCRv6");
        assertThat(result.modelSource()).isEqualTo("bettergi");
        assertThat(result.imageWidth()).isEqualTo(1280);
        assertThat(result.imageHeight()).isEqualTo(10892);
        assertThat(result.blocks()).singleElement().satisfies(block -> {
            assertThat(block.text()).isEqualTo("摩拉");
            assertThat(block.confidence()).isEqualTo(0.99);
            assertThat(block.left()).isEqualTo(120.0);
            assertThat(block.bottom()).isEqualTo(180.0);
        });
    }

    @Test
    void reportsProcessFailureWithStderr() throws Exception {
        CultivationOcrProcess process = mock(CultivationOcrProcess.class);
        when(process.run(any(), any())).thenReturn(
                new CultivationOcrProcessResult(2, "", "onnxruntime is not installed"));

        RapidOcrV6ProcessEngine engine = new RapidOcrV6ProcessEngine(
                process, new ObjectMapper(), new CultivationOcrProperties(),
                tempDirectory.resolve("rapidocr_v6_bridge.py"), null);

        assertThatThrownBy(() -> engine.recognize(tempDirectory.resolve("calculator.png")))
                .isInstanceOf(CultivationOcrException.class)
                .hasMessageContaining("onnxruntime is not installed");
    }
}
