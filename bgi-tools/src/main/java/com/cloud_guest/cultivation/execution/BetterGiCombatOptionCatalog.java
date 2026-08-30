package com.cloud_guest.cultivation.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BetterGiCombatOptionCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterGiCombatOptionCatalog.class);
    private static final Set<String> PARTY_KEYS = Set.of(
            "partyname", "teamname", "friendshipteam", "fightteamname", "bosspartyname", "team_fight");
    private static final Set<String> STRATEGY_KEYS = Set.of(
            "strategyname", "autofightstrategyname", "bossstrategyname");

    private final CultivationMaterialSourceCatalog sourceCatalog;
    private final ObjectMapper objectMapper;

    public BetterGiCombatOptionCatalog(CultivationMaterialSourceCatalog sourceCatalog,
                                       ObjectMapper objectMapper) {
        this.sourceCatalog = sourceCatalog;
        this.objectMapper = objectMapper;
    }

    public Options discover() {
        Path root;
        try {
            root = sourceCatalog.betterGiRoot();
        } catch (IllegalStateException exception) {
            return new Options(List.of(), List.of("根据队伍自动选择"));
        }

        Set<String> parties = new LinkedHashSet<>();
        Set<String> strategies = new LinkedHashSet<>();
        strategies.add("根据队伍自动选择");
        discoverStrategyFiles(root.resolve(Path.of("User", "AutoFight")), strategies);
        inspectJson(root.resolve(Path.of("User", "config.json")), parties, strategies);
        inspectScriptGroups(root.resolve(Path.of("User", "ScriptGroup")), parties, strategies);
        return new Options(sorted(parties), sortedStrategies(strategies));
    }

    private void inspectScriptGroups(Path directory, Set<String> parties, Set<String> strategies) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> inspectJson(path, parties, strategies));
        } catch (IOException exception) {
            throw new IllegalStateException("无法扫描 BetterGI 配置组中的队伍与战斗策略", exception);
        }
    }

    private void inspectJson(Path file, Set<String> parties, Set<String> strategies) {
        if (!Files.isRegularFile(file)) return;
        try {
            collect(objectMapper.readTree(file.toFile()), parties, strategies);
        } catch (IOException exception) {
            LOGGER.warn("跳过无法读取的 BetterGI 作战配置：{} ({})", file, exception.getMessage());
        }
    }

    private static void collect(JsonNode node, Set<String> parties, Set<String> strategies) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    if (PARTY_KEYS.contains(key)) add(parties, value.asText());
                    if (STRATEGY_KEYS.contains(key)) add(strategies, value.asText());
                }
                collect(value, parties, strategies);
            });
            return;
        }
        if (node.isArray()) node.forEach(child -> collect(child, parties, strategies));
    }

    private static void discoverStrategyFiles(Path directory, Set<String> strategies) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".txt") || name.endsWith(".json"))
                    .map(BetterGiCombatOptionCatalog::withoutExtension)
                    .forEach(name -> add(strategies, name));
        } catch (IOException exception) {
            throw new IllegalStateException("无法扫描 BetterGI 战斗策略目录", exception);
        }
    }

    private static void add(Set<String> target, String value) {
        if (value == null) return;
        String normalized = value.trim();
        if (!normalized.isEmpty()) target.add(normalized);
    }

    private static String withoutExtension(String name) {
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static List<String> sortedStrategies(Set<String> values) {
        List<String> result = new ArrayList<>();
        if (values.remove("根据队伍自动选择")) result.add("根据队伍自动选择");
        result.addAll(sorted(values));
        return List.copyOf(result);
    }

    public record Options(List<String> parties, List<String> strategies) {
        public Options {
            parties = List.copyOf(parties);
            strategies = List.copyOf(strategies);
        }
    }
}
