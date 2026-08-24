package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.ocr.CultivationOcrBlock;
import com.cloud_guest.cultivation.ocr.CultivationRequirementRow;
import com.cloud_guest.cultivation.ocr.OcrPoint;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CultivationImportConfirmationAssemblerTest {

    @Test
    void keepsOcrEvidenceWhileSupportingCorrectionDeletionAndManualRows() {
        CultivationOcrBlock sourceBlock = new CultivationOcrBlock(
                "摩拉", 0.91, List.of(new OcrPoint(10, 20), new OcrPoint(30, 40)));
        List<CultivationRequirementRow> original = List.of(
                new CultivationRequirementRow("摩拉", 100, 40, 0.91, false,
                        RemainingEvidence.OCR, List.of(sourceBlock)),
                new CultivationRequirementRow("精锻用魔矿", 10, 0, 0.98, false,
                        RemainingEvidence.INFERRED_ZERO, List.of())
        );
        List<CultivationRequirementEdit> edits = List.of(
                new CultivationRequirementEdit(0, "摩拉", 100, 35),
                new CultivationRequirementEdit(null, "大英雄的经验", 20, 12)
        );

        List<CultivationLedgerEntry> ledger =
                new CultivationImportConfirmationAssembler().assemble(original, edits);

        assertThat(ledger).hasSize(2);
        assertThat(ledger.getFirst()).satisfies(entry -> {
            assertThat(entry.materialName()).isEqualTo("摩拉");
            assertThat(entry.baselineOwned()).isEqualTo(65);
            assertThat(entry.remainingEvidence()).isEqualTo(RemainingEvidence.MANUAL);
            assertThat(entry.manuallyCorrected()).isTrue();
            assertThat(entry.ocrConfidence()).isEqualTo(0.91);
            assertThat(entry.sourceBlocks()).containsExactly(sourceBlock);
        });
        assertThat(ledger.get(1)).satisfies(entry -> {
            assertThat(entry.materialName()).isEqualTo("大英雄的经验");
            assertThat(entry.baselineOwned()).isEqualTo(8);
            assertThat(entry.remainingEvidence()).isEqualTo(RemainingEvidence.MANUAL);
            assertThat(entry.ocrConfidence()).isNull();
            assertThat(entry.manuallyCorrected()).isTrue();
        });
    }

    @Test
    void rejectsDuplicateMaterialsAndImpossibleRemainingCounts() {
        CultivationImportConfirmationAssembler assembler =
                new CultivationImportConfirmationAssembler();

        assertThatThrownBy(() -> assembler.assemble(List.of(), List.of(
                new CultivationRequirementEdit(null, "摩拉", 100, 20),
                new CultivationRequirementEdit(null, " 摩拉 ", 100, 10)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复材料");

        assertThatThrownBy(() -> assembler.assemble(List.of(), List.of(
                new CultivationRequirementEdit(null, "智识之冕", 10, 11)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("还需数量不能大于总需求");
    }
}
