package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.ocr.CultivationCalculatorParser;
import com.cloud_guest.cultivation.ocr.CultivationOcrBlock;
import com.cloud_guest.cultivation.ocr.CultivationOcrEngine;
import com.cloud_guest.cultivation.ocr.CultivationOcrResult;
import com.cloud_guest.cultivation.ocr.OcrPoint;
import com.cloud_guest.cultivation.persistence.CultivationImportPreviewEntity;
import com.cloud_guest.cultivation.persistence.CultivationImportPreviewMapper;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionEntity;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CultivationPlanApplicationServiceTest {

    @Test
    void serializesSnowflakeIdsAsStringsForBrowserSafety() {
        long snowflakeId = 2091496460834091009L;
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        JsonNode previewJson = objectMapper.valueToTree(new CultivationImportPreviewResponse(
                snowflakeId, "123456789", "sha256", "PP-OCRv6", "bettergi-installed-assets",
                1280, 10892, List.of(), List.of()));
        JsonNode revisionJson = objectMapper.valueToTree(new CultivationPlanRevisionResponse(
                snowflakeId, "123456789", 1, "IMPORTED", "name-only-v1", snowflakeId,
                "sha256", "PP-OCRv6", "bettergi-installed-assets", List.of(), LocalDateTime.now()));

        assertThat(previewJson.path("previewId").isTextual()).isTrue();
        assertThat(previewJson.path("previewId").asText()).isEqualTo(Long.toString(snowflakeId));
        assertThat(revisionJson.path("id").isTextual()).isTrue();
        assertThat(revisionJson.path("previewId").isTextual()).isTrue();
    }

    @Test
    void createsCorrectablePreviewThenPersistsNextImmutableRevision() {
        CultivationOcrEngine ocrEngine = mock(CultivationOcrEngine.class);
        when(ocrEngine.recognize(any(Path.class))).thenReturn(new CultivationOcrResult(
                "RapidOCR 3.9.2 / PP-OCRv6", "bettergi-installed-assets", 1280, 10892,
                calculatorBlocks()));

        CultivationImportPreviewMapper previewMapper = mock(CultivationImportPreviewMapper.class);
        CultivationPlanRevisionMapper revisionMapper = mock(CultivationPlanRevisionMapper.class);
        AtomicReference<CultivationImportPreviewEntity> savedPreview = new AtomicReference<>();
        doAnswer(invocation -> {
            CultivationImportPreviewEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            savedPreview.set(entity);
            return 1;
        }).when(previewMapper).insert(any());
        doAnswer(invocation -> {
            CultivationPlanRevisionEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(revisionMapper).insert(any());
        when(revisionMapper.findMaxRevision("123456789")).thenReturn(2);

        CultivationPlanApplicationService service = new CultivationPlanApplicationService(
                ocrEngine,
                new CultivationCalculatorParser(),
                new CultivationImportConfirmationAssembler(),
                previewMapper,
                revisionMapper,
                new ObjectMapper().findAndRegisterModules());

        MockMultipartFile image = new MockMultipartFile(
                "file", "calculator.png", "application/octet-stream", new byte[]{1, 2, 3, 4});
        CultivationImportPreviewResponse preview = service.preview("123456789", image);

        assertThat(preview.previewId()).isEqualTo(42L);
        assertThat(preview.engineVersion()).contains("PP-OCRv6");
        assertThat(preview.modelSource()).isEqualTo("bettergi-installed-assets");
        assertThat(preview.requirements()).hasSize(2);
        assertThat(preview.requirements().getFirst().sourceIndex()).isZero();
        assertThat(savedPreview.get().getRawOcrJson()).contains("摩拉");
        assertThat(savedPreview.get().getStatus()).isEqualTo("DRAFT");

        when(previewMapper.selectById(42L)).thenReturn(savedPreview.get());
        ConfirmCultivationImportRequest request = new ConfirmCultivationImportRequest(
                42L,
                "123456789",
                List.of(
                        new CultivationRequirementEdit(0, "摩拉", 100, 30),
                        new CultivationRequirementEdit(1, "大英雄的经验", 20, 12)
                ));
        CultivationPlanRevisionResponse confirmed = service.confirm(request);

        assertThat(confirmed.id()).isEqualTo(99L);
        assertThat(confirmed.revision()).isEqualTo(3);
        assertThat(confirmed.state()).isEqualTo("IMPORTED");
        assertThat(confirmed.requirements().getFirst()).satisfies(entry -> {
            assertThat(entry.remaining()).isEqualTo(30);
            assertThat(entry.baselineOwned()).isEqualTo(70);
            assertThat(entry.manuallyCorrected()).isTrue();
        });
        assertThat(savedPreview.get().getStatus()).isEqualTo("CONFIRMED");
        assertThat(savedPreview.get().getPlanRevisionId()).isEqualTo(99L);
        verify(previewMapper).updateById(savedPreview.get());
    }

    private static List<CultivationOcrBlock> calculatorBlocks() {
        List<CultivationOcrBlock> blocks = new ArrayList<>();
        blocks.add(block("材料", 1, 350, 100, 430, 125));
        blocks.add(block("消耗", 1, 720, 100, 800, 125));
        blocks.add(block("还需", 1, 930, 100, 1010, 125));
        blocks.add(block("摩拉", 0.99, 120, 150, 220, 180));
        blocks.add(block("100", 0.99, 730, 150, 790, 180));
        blocks.add(block("40", 0.99, 945, 150, 1000, 180));
        blocks.add(block("大英雄的经验", 0.98, 120, 195, 300, 225));
        blocks.add(block("20", 0.99, 735, 195, 790, 225));
        blocks.add(block("12", 0.99, 945, 195, 1000, 225));
        blocks.add(block("背包内多余可合成的材料", 1, 120, 250, 450, 280));
        return blocks;
    }

    private static CultivationOcrBlock block(String text, double confidence,
                                              double left, double top, double right, double bottom) {
        return new CultivationOcrBlock(text, confidence, List.of(
                new OcrPoint(left, top), new OcrPoint(right, top),
                new OcrPoint(right, bottom), new OcrPoint(left, bottom)));
    }
}
