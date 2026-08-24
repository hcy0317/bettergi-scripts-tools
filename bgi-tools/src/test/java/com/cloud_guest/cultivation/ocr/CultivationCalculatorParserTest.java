package com.cloud_guest.cultivation.ocr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationCalculatorParserTest {

    @Test
    void parsesRequirementRowsByGeometryAndStopsBeforeInventorySection() {
        List<CultivationOcrBlock> blocks = new ArrayList<>(List.of(
                block("材料", 1.0, 350, 100, 430, 125),
                block("消耗", 1.0, 720, 100, 800, 125),
                block("还需", 1.0, 930, 100, 1010, 125),
                block("摩拉", 0.99, 120, 150, 220, 180),
                block("31964305", 0.99, 710, 150, 830, 180),
                block("7621233", 0.98, 920, 150, 1030, 180),
                block("大英雄的经验", 0.97, 120, 195, 300, 225),
                block("1528", 0.99, 730, 195, 800, 225),
                block("1221", 0.99, 940, 195, 1010, 225),
                block("精锻用魔矿", 0.96, 120, 240, 285, 270),
                block("605", 0.99, 735, 240, 795, 270),
                block("背包内多余可合成的材料", 0.99, 120, 310, 450, 340),
                block("999", 1.0, 720, 360, 790, 390)
        ));

        CultivationParseResult result = new CultivationCalculatorParser().parse(blocks);

        assertThat(result.requirements()).hasSize(3);
        assertThat(result.requirements().getFirst())
                .extracting(CultivationRequirementRow::materialName,
                        CultivationRequirementRow::required,
                        CultivationRequirementRow::remaining,
                        CultivationRequirementRow::observedOwned,
                        CultivationRequirementRow::remainingEvidence)
                .containsExactly("摩拉", 31_964_305L, 7_621_233L, 24_343_072L,
                        RemainingEvidence.OCR);
        assertThat(result.requirements().get(2))
                .extracting(CultivationRequirementRow::materialName,
                        CultivationRequirementRow::required,
                        CultivationRequirementRow::remaining,
                        CultivationRequirementRow::remainingEvidence)
                .containsExactly("精锻用魔矿", 605L, 0L, RemainingEvidence.INFERRED_ZERO);
    }

    @Test
    void normalizesFullWidthDigitsAndFlagsImpossibleOrLowConfidenceRows() {
        List<CultivationOcrBlock> blocks = List.of(
                block("材料", 1.0, 350, 100, 430, 125),
                block("消耗", 1.0, 720, 100, 800, 125),
                block("还需", 1.0, 930, 100, 1010, 125),
                block("智识之冕", 0.68, 120, 150, 250, 180),
                block("１５", 0.99, 735, 150, 790, 180),
                block("１７", 0.99, 945, 150, 1000, 180)
        );

        CultivationParseResult result = new CultivationCalculatorParser().parse(blocks);

        assertThat(result.requirements()).singleElement().satisfies(row -> {
            assertThat(row.required()).isEqualTo(15);
            assertThat(row.remaining()).isEqualTo(17);
            assertThat(row.needsReview()).isTrue();
        });
        assertThat(result.warnings()).anyMatch(message -> message.contains("智识之冕"));
    }

    private static CultivationOcrBlock block(String text, double confidence,
                                              double left, double top, double right, double bottom) {
        return new CultivationOcrBlock(text, confidence, List.of(
                new OcrPoint(left, top),
                new OcrPoint(right, top),
                new OcrPoint(right, bottom),
                new OcrPoint(left, bottom)
        ));
    }
}
