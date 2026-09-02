package com.cloud_guest.cultivation.execution;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CultivationMaterialCraftingCatalog {
    private static final Set<String> CRAFTABLE_TYPES = Set.of(
            "角色突破素材", "角色天赋素材", "角色与武器培养素材", "武器突破素材");

    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private volatile Map<String, CraftFamily> familyByMaterial;

    public CultivationMaterialCraftingCatalog(CultivationMaterialSourceCatalog materialSourceCatalog) {
        this.materialSourceCatalog = materialSourceCatalog;
    }

    public Optional<CraftFamily> family(String materialName) {
        return Optional.ofNullable(families().get(materialName));
    }

    private Map<String, CraftFamily> families() {
        Map<String, CraftFamily> current = familyByMaterial;
        if (current != null) return current;
        synchronized (this) {
            if (familyByMaterial != null) return familyByMaterial;
            Map<String, CraftFamily> loaded = loadFamilies();
            if (!loaded.isEmpty()) familyByMaterial = loaded;
            return loaded;
        }
    }

    private Map<String, CraftFamily> loadFamilies() {
        Path root;
        try {
            root = materialSourceCatalog.betterGiRoot();
        } catch (IllegalStateException exception) {
            return Map.of();
        }
        Path csv = root.resolve(Path.of("Assets", "Model", "ItemV2", "item.csv"));
        if (!Files.isRegularFile(csv)) return Map.of();
        try {
            List<String> lines = Files.readAllLines(csv);
            if (lines.isEmpty()) return Map.of();
            List<String> headers = parseCsvLine(lines.getFirst());
            int idIndex = headers.indexOf("item_class_id");
            int nameIndex = headers.indexOf("item_name");
            int typeIndex = headers.indexOf("material_type");
            int qualityIndex = headers.indexOf("quality_level");
            if (idIndex < 0 || nameIndex < 0 || typeIndex < 0 || qualityIndex < 0) return Map.of();

            List<CraftTier> tiers = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                List<String> values = parseCsvLine(line);
                if (values.size() <= Math.max(Math.max(idIndex, nameIndex), Math.max(typeIndex, qualityIndex))) continue;
                String type = values.get(typeIndex).trim();
                int quality = parseInteger(values.get(qualityIndex), -1);
                int id = parseMaterialId(values.get(idIndex));
                String name = values.get(nameIndex).trim();
                if (!CRAFTABLE_TYPES.contains(type)
                        || quality <= 0
                        || quality > maxCraftableQuality(type)
                        || id < 0
                        || name.isBlank()) {
                    continue;
                }
                tiers.add(new CraftTier(id, name, type, quality));
            }
            tiers.sort(Comparator.comparingInt(CraftTier::materialId));

            Map<String, CraftFamily> result = new LinkedHashMap<>();
            List<CraftTier> current = new ArrayList<>();
            for (CraftTier tier : tiers) {
                CraftTier previous = current.isEmpty() ? null : current.getLast();
                if (previous != null && (tier.materialId() != previous.materialId() + 1
                        || tier.qualityLevel() != previous.qualityLevel() + 1
                        || !tier.materialType().equals(previous.materialType()))) {
                    registerFamily(current, result);
                    current = new ArrayList<>();
                }
                current.add(tier);
            }
            registerFamily(current, result);
            return Map.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 多级养成材料目录", exception);
        }
    }

    private static void registerFamily(List<CraftTier> tiers, Map<String, CraftFamily> target) {
        if (tiers.size() < 2) return;
        CraftFamily family = new CraftFamily(tiers.getLast().materialName(), List.copyOf(tiers));
        tiers.forEach(tier -> target.put(tier.materialName(), family));
    }

    private static int parseMaterialId(String value) {
        int separator = value.lastIndexOf(':');
        return parseInteger(separator >= 0 ? value.substring(separator + 1) : value, -1);
    }

    private static int maxCraftableQuality(String materialType) {
        return switch (materialType) {
            case "角色天赋素材" -> 4;
            case "角色与武器培养素材" -> 3;
            case "角色突破素材", "武器突破素材" -> 5;
            default -> 0;
        };
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        values.add(value.toString());
        return values;
    }

    public record CraftFamily(String familyName, List<CraftTier> tiers) {
    }

    public record CraftTier(int materialId, String materialName, String materialType, int qualityLevel) {
    }
}
