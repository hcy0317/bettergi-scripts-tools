package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.ocr.CultivationRequirementRow;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CultivationImportConfirmationAssembler {

    public List<CultivationLedgerEntry> assemble(List<CultivationRequirementRow> originalRows,
                                                  List<CultivationRequirementEdit> edits) {
        List<CultivationRequirementRow> originals = originalRows == null ? List.of() : originalRows;
        if (edits == null || edits.isEmpty()) {
            throw new IllegalArgumentException("确认材料不能为空");
        }

        List<CultivationLedgerEntry> result = new ArrayList<>();
        Set<String> materialNames = new HashSet<>();
        Set<Integer> usedSourceIndexes = new HashSet<>();
        for (CultivationRequirementEdit edit : edits) {
            if (edit == null) {
                throw new IllegalArgumentException("材料编辑项不能为空");
            }
            String materialName = edit.materialName() == null ? "" : edit.materialName().trim();
            if (materialName.isBlank()) {
                throw new IllegalArgumentException("材料名称不能为空");
            }
            if (!materialNames.add(materialName)) {
                throw new IllegalArgumentException("存在重复材料：" + materialName);
            }
            if (edit.required() < 0 || edit.remaining() < 0) {
                throw new IllegalArgumentException("材料数量不能为负数：" + materialName);
            }
            if (edit.remaining() > edit.required()) {
                throw new IllegalArgumentException("还需数量不能大于总需求：" + materialName);
            }

            CultivationRequirementRow original = null;
            if (edit.sourceIndex() != null) {
                int index = edit.sourceIndex();
                if (index < 0 || index >= originals.size()) {
                    throw new IllegalArgumentException("OCR 来源索引无效：" + index);
                }
                if (!usedSourceIndexes.add(index)) {
                    throw new IllegalArgumentException("OCR 来源索引被重复使用：" + index);
                }
                original = originals.get(index);
            }

            boolean corrected = original == null
                    || !materialName.equals(original.materialName())
                    || edit.required() != original.required()
                    || edit.remaining() != original.remaining();
            RemainingEvidence evidence = corrected
                    ? RemainingEvidence.MANUAL
                    : original.remainingEvidence();

            result.add(new CultivationLedgerEntry(
                    edit.sourceIndex(),
                    materialName,
                    edit.required(),
                    Math.max(edit.required() - edit.remaining(), 0),
                    edit.remaining(),
                    evidence,
                    original == null ? null : original.confidence(),
                    corrected,
                    original == null ? List.of() : original.sourceBlocks()
            ));
        }
        return List.copyOf(result);
    }
}
