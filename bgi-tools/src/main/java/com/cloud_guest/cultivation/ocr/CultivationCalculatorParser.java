package com.cloud_guest.cultivation.ocr;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CultivationCalculatorParser {
    private static final double REVIEW_CONFIDENCE = 0.75;

    public CultivationParseResult parse(List<CultivationOcrBlock> source) {
        List<CultivationOcrBlock> blocks = source == null
                ? List.of()
                : source.stream()
                .filter(block -> block != null && block.text() != null && !block.text().isBlank())
                .sorted(Comparator.comparingDouble(CultivationOcrBlock::centerY)
                        .thenComparingDouble(CultivationOcrBlock::centerX))
                .toList();

        Optional<Headers> headers = findHeaders(blocks);
        if (headers.isEmpty()) {
            return new CultivationParseResult(List.of(), List.of("未识别到‘材料 / 消耗 / 还需’表头"));
        }

        Headers h = headers.get();
        double endY = blocks.stream()
                .filter(block -> block.top() > h.bottom())
                .filter(block -> isSectionBoundary(normalizeText(block.text())))
                .mapToDouble(CultivationOcrBlock::top)
                .min()
                .orElse(Double.POSITIVE_INFINITY);

        double rowTolerance = Math.max(8.0, h.height() * 0.8);
        List<List<CultivationOcrBlock>> lines = groupRows(
                blocks.stream()
                        .filter(block -> block.centerY() > h.bottom())
                        .filter(block -> block.centerY() < endY)
                        .toList(),
                rowTolerance);

        double materialBoundary = (h.material().centerX() + h.required().centerX()) / 2.0;
        double numberBoundary = (h.required().centerX() + h.remaining().centerX()) / 2.0;
        Map<String, CultivationRequirementRow> requirements = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (List<CultivationOcrBlock> line : lines) {
            List<CultivationOcrBlock> nameBlocks = line.stream()
                    .filter(block -> block.centerX() < materialBoundary)
                    .filter(block -> parseNumber(block.text()).isEmpty())
                    .sorted(Comparator.comparingDouble(CultivationOcrBlock::left))
                    .toList();
            if (nameBlocks.isEmpty()) {
                continue;
            }

            String materialName = nameBlocks.stream()
                    .map(CultivationOcrBlock::text)
                    .map(CultivationCalculatorParser::normalizeText)
                    .reduce("", String::concat);
            if (materialName.isBlank() || isSectionBoundary(materialName)) {
                continue;
            }

            Optional<CultivationOcrBlock> requiredBlock = line.stream()
                    .filter(block -> block.centerX() >= materialBoundary && block.centerX() < numberBoundary)
                    .filter(block -> parseNumber(block.text()).isPresent())
                    .min(Comparator.comparingDouble(block -> Math.abs(block.centerX() - h.required().centerX())));
            if (requiredBlock.isEmpty()) {
                continue;
            }

            Optional<CultivationOcrBlock> remainingBlock = line.stream()
                    .filter(block -> block.centerX() >= numberBoundary)
                    .filter(block -> parseNumber(block.text()).isPresent())
                    .min(Comparator.comparingDouble(block -> Math.abs(block.centerX() - h.remaining().centerX())));

            long required = parseNumber(requiredBlock.get().text()).orElseThrow();
            long remaining = remainingBlock.flatMap(block -> parseNumber(block.text())).orElse(0L);
            RemainingEvidence evidence = remainingBlock.isPresent()
                    ? RemainingEvidence.OCR
                    : RemainingEvidence.INFERRED_ZERO;
            List<CultivationOcrBlock> rowBlocks = new ArrayList<>(nameBlocks);
            rowBlocks.add(requiredBlock.get());
            remainingBlock.ifPresent(rowBlocks::add);

            double confidence = rowBlocks.stream()
                    .mapToDouble(CultivationOcrBlock::confidence)
                    .min()
                    .orElse(0);
            boolean needsReview = confidence < REVIEW_CONFIDENCE || remaining > required;
            if (needsReview) {
                warnings.add("材料‘" + materialName + "’的识别结果需要确认");
            }

            CultivationRequirementRow row = new CultivationRequirementRow(
                    materialName, required, remaining, confidence, needsReview, evidence, rowBlocks);
            requirements.merge(materialName, row,
                    (existing, replacement) -> replacement.confidence() > existing.confidence()
                            ? replacement
                            : existing);
        }

        if (requirements.isEmpty()) {
            warnings.add("表头已识别，但没有解析出材料行");
        }
        return new CultivationParseResult(new ArrayList<>(requirements.values()), warnings);
    }

    private static Optional<Headers> findHeaders(List<CultivationOcrBlock> blocks) {
        List<CultivationOcrBlock> requiredHeaders = blocks.stream()
                .filter(block -> "消耗".equals(normalizeText(block.text())))
                .toList();
        for (CultivationOcrBlock required : requiredHeaders) {
            double tolerance = Math.max(12.0, required.height() * 1.5);
            Optional<CultivationOcrBlock> material = blocks.stream()
                    .filter(block -> "材料".equals(normalizeText(block.text())))
                    .filter(block -> block.centerX() < required.centerX())
                    .filter(block -> Math.abs(block.centerY() - required.centerY()) <= tolerance)
                    .min(Comparator.comparingDouble(block -> Math.abs(block.centerY() - required.centerY())));
            Optional<CultivationOcrBlock> remaining = blocks.stream()
                    .filter(block -> "还需".equals(normalizeText(block.text())))
                    .filter(block -> block.centerX() > required.centerX())
                    .filter(block -> Math.abs(block.centerY() - required.centerY()) <= tolerance)
                    .min(Comparator.comparingDouble(block -> Math.abs(block.centerY() - required.centerY())));
            if (material.isPresent() && remaining.isPresent()) {
                return Optional.of(new Headers(material.get(), required, remaining.get()));
            }
        }
        return Optional.empty();
    }

    private static List<List<CultivationOcrBlock>> groupRows(List<CultivationOcrBlock> blocks,
                                                              double tolerance) {
        List<List<CultivationOcrBlock>> rows = new ArrayList<>();
        for (CultivationOcrBlock block : blocks) {
            if (rows.isEmpty()) {
                rows.add(new ArrayList<>(List.of(block)));
                continue;
            }
            List<CultivationOcrBlock> current = rows.getLast();
            double rowCenter = current.stream().mapToDouble(CultivationOcrBlock::centerY).average().orElse(0);
            if (Math.abs(block.centerY() - rowCenter) <= tolerance) {
                current.add(block);
            } else {
                rows.add(new ArrayList<>(List.of(block)));
            }
        }
        return rows;
    }

    private static Optional<Long> parseNumber(String source) {
        String normalized = normalizeDigits(normalizeText(source))
                .replace(",", "")
                .replace("，", "");
        if (!normalized.matches("\\d+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeDigits(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            if (character >= '０' && character <= '９') {
                result.append((char) ('0' + character - '０'));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }

    private static boolean isSectionBoundary(String text) {
        return text.contains("背包内")
                || text.contains("养成材料明细")
                || text.contains("角色养成材料")
                || text.contains("天赋养成材料")
                || text.contains("武器养成材料");
    }

    private record Headers(CultivationOcrBlock material,
                           CultivationOcrBlock required,
                           CultivationOcrBlock remaining) {
        double bottom() {
            return Math.max(material.bottom(), Math.max(required.bottom(), remaining.bottom()));
        }

        double height() {
            return Math.max(material.height(), Math.max(required.height(), remaining.height()));
        }
    }
}
