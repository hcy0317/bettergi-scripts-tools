package com.cloud_guest.cultivation.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationSampleImageIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "BGI_OCR_INTEGRATION", matches = "true")
    void recognizesAndParsesExportedCalculatorImageWithBetterGiV6Assets() {
        CultivationOcrProperties properties = new CultivationOcrProperties();
        properties.setPythonCommand(requiredEnvironment("BGI_OCR_PYTHON"));
        RapidOcrV6ProcessEngine engine = new RapidOcrV6ProcessEngine(
                new SystemCultivationOcrProcess(),
                new ObjectMapper(),
                properties,
                Path.of("src/main/resources/ocr/rapidocr_v6_bridge.py"),
                Path.of(requiredEnvironment("BETTERGI_ROOT")));

        CultivationOcrResult ocrResult = engine.recognize(
                Path.of(requiredEnvironment("BGI_OCR_SAMPLE")));
        CultivationParseResult parsed = new CultivationCalculatorParser().parse(ocrResult.blocks());

        assertThat(ocrResult.engineVersion()).contains("PP-OCRv6");
        assertThat(ocrResult.modelSource()).isEqualTo("bettergi-installed-assets");
        assertThat(parsed.requirements()).hasSizeGreaterThan(50);
        assertThat(parsed.requirements())
                .filteredOn(row -> row.materialName().equals("摩拉"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.required()).isEqualTo(31_964_305);
                    assertThat(row.remaining()).isEqualTo(7_621_233);
                });
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
