package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.CultivationOcrProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Component
public class CultivationMaterialSourceCatalog {
    private static final Path MONSTER_INFO = Path.of(
            "User", "JsScript", "AutoHoeingOneDragon", "assets", "monsterInfo.json");
    private static final Path MONSTER_ROUTES = Path.of("User", "AutoPathing", "敌人与魔物");
    private static final Path SPECIALTY_ROUTES = Path.of("User", "AutoPathing", "地方特产");

    private final CultivationOcrProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, BossSource> bossDrops;
    private final Map<String, String> weeklyBossDrops;
    private final Map<String, String> monsterRouteAliases;
    private volatile Path resolvedBetterGiRoot;

    public CultivationMaterialSourceCatalog(CultivationOcrProperties properties,
                                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        CatalogData data = loadCatalogData(objectMapper);
        this.bossDrops = data.bossDrops();
        this.weeklyBossDrops = data.weeklyBossDrops();
        this.monsterRouteAliases = data.monsterRouteAliases();
    }

    public Optional<BossSource> findBoss(String materialName) {
        return Optional.ofNullable(bossDrops.get(materialName));
    }

    public Optional<String> findWeeklyBoss(String materialName) {
        return Optional.ofNullable(weeklyBossDrops.get(materialName));
    }

    public Optional<MonsterSource> findMonster(String materialName) {
        Optional<Path> discoveredRoot = betterGiRootForRead();
        if (discoveredRoot.isEmpty()) return Optional.empty();
        Path root = discoveredRoot.get();
        Path monsterInfo = root.resolve(MONSTER_INFO);
        if (!Files.isRegularFile(monsterInfo)) return Optional.empty();

        try {
            List<String> routeFamilies = availableMonsterRouteFamilies(root);
            List<String> monsterNames = new ArrayList<>();
            Set<String> tags = new LinkedHashSet<>();
            JsonNode entries = objectMapper.readTree(monsterInfo.toFile());
            for (JsonNode entry : entries) {
                if (!contains(entry.path("item"), materialName)) continue;
                String monsterName = entry.path("name").asText("");
                if (!monsterName.isBlank()) monsterNames.add(monsterName);
                entry.path("tags").forEach(tag -> tags.add(tag.asText()));
            }
            if (monsterNames.isEmpty()) return Optional.empty();

            String family = resolveRouteFamily(materialName, monsterNames, tags, routeFamilies);
            if (family == null) return Optional.empty();
            return Optional.of(new MonsterSource(family, List.copyOf(monsterNames), routeFamilies));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 怪物材料目录", exception);
        }
    }

    public Optional<String> findSpecialtyCountry(String materialName) {
        if (materialName == null || materialName.isBlank()) return Optional.empty();
        Optional<Path> discoveredRoot = betterGiRootForRead();
        if (discoveredRoot.isEmpty()) return Optional.empty();
        Path routeRoot = discoveredRoot.get().resolve(SPECIALTY_ROUTES);
        if (!Files.isDirectory(routeRoot)) return Optional.empty();
        try (var countries = Files.list(routeRoot)) {
            for (Path country : countries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                Path materialRoot = country.resolve(materialName);
                if (!Files.isDirectory(materialRoot)) continue;
                try (var paths = Files.walk(materialRoot)) {
                    if (paths.anyMatch(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".json"))) {
                        return Optional.of(country.getFileName().toString());
                    }
                }
            }
            return Optional.empty();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 地方特产路线目录", exception);
        }
    }

    public List<String> availableMonsterRouteFamilies() {
        return betterGiRootForRead()
                .map(CultivationMaterialSourceCatalog::availableMonsterRouteFamilies)
                .orElseGet(List::of);
    }

    private Optional<Path> betterGiRootForRead() {
        try {
            return Optional.of(betterGiRoot());
        } catch (IllegalStateException exception) {
            return Optional.empty();
        }
    }

    public Path betterGiRoot() {
        Path current = resolvedBetterGiRoot;
        if (current != null) return current;
        synchronized (this) {
            if (resolvedBetterGiRoot != null) return resolvedBetterGiRoot;
            resolvedBetterGiRoot = resolveBetterGiRoot(
                    properties.getBettergiRoot(),
                    Path.of(System.getProperty("user.dir")),
                    CultivationMaterialSourceCatalog::runningProcessCommands,
                    CultivationMaterialSourceCatalog::knownInstallationCandidates)
                    .orElseThrow(() -> new IllegalStateException(
                            "未找到 BetterGI 根目录，请设置 BETTERGI_ROOT"));
            return resolvedBetterGiRoot;
        }
    }

    static Optional<Path> resolveBetterGiRoot(String configuredRoot,
                                              Path workingDirectory,
                                              Supplier<List<Path>> runningCommands,
                                              Supplier<List<Path>> installationCandidates) {
        Optional<Path> known = resolveBetterGiRoot(
                configuredRoot, workingDirectory, List.of(), List.of());
        if (known.isPresent()) return known;
        return resolveBetterGiRoot(
                "", workingDirectory, runningCommands.get(), installationCandidates.get());
    }

    static Optional<Path> resolveBetterGiRoot(String configuredRoot,
                                              Path workingDirectory,
                                              List<Path> runningCommands,
                                              List<Path> installationCandidates) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Optional.of(Path.of(configuredRoot).toAbsolutePath().normalize());
        }
        Path current = workingDirectory.toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("User").resolve("ScriptGroup"))) {
                return Optional.of(current);
            }
        }
        for (Path command : runningCommands) {
            if (command.getFileName() == null
                    || !command.getFileName().toString().equalsIgnoreCase("BetterGI.exe")) continue;
            Path candidate = command.toAbsolutePath().normalize().getParent();
            if (isBetterGiInstallation(candidate)) return Optional.of(candidate);
        }
        return installationCandidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(CultivationMaterialSourceCatalog::isBetterGiInstallation)
                .findFirst();
    }

    private static boolean isBetterGiInstallation(Path root) {
        return root != null
                && Files.isDirectory(root.resolve("User"))
                && Files.isRegularFile(root.resolve("BetterGI.exe"));
    }

    private static List<Path> runningProcessCommands() {
        try (var processes = ProcessHandle.allProcesses()) {
            return processes
                    .map(process -> process.info().command())
                    .flatMap(Optional::stream)
                    .map(CultivationMaterialSourceCatalog::pathOrNull)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (SecurityException exception) {
            return List.of();
        }
    }

    private static Path pathOrNull(String value) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private static List<Path> knownInstallationCandidates() {
        List<Path> candidates = new ArrayList<>();
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            candidates.add(Path.of(userHome, "Programs", "Genshin Tools", "BetterGI"));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(Path.of(localAppData, "BetterGI"));
        }
        return List.copyOf(candidates);
    }

    private String resolveRouteFamily(String materialName,
                                      List<String> monsterNames,
                                      Set<String> tags,
                                      List<String> routeFamilies) {
        for (String tag : tags) {
            if (routeFamilies.contains(tag)) return tag;
        }
        for (String family : routeFamilies) {
            if (monsterNames.stream().anyMatch(name -> name.startsWith(family) || name.contains(family))) {
                return family;
            }
        }
        String alias = monsterRouteAliases.get(materialName);
        return routeFamilies.contains(alias) ? alias : null;
    }

    private static List<String> availableMonsterRouteFamilies(Path root) {
        Path routeRoot = root.resolve(MONSTER_ROUTES);
        if (!Files.isDirectory(routeRoot)) return List.of();
        try (var paths = Files.list(routeRoot)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 敌人与魔物路线目录", exception);
        }
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.asText())) return true;
        }
        return false;
    }

    private static CatalogData loadCatalogData(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("cultivation/material-sources.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            Map<String, BossSource> bossDrops = new LinkedHashMap<>();
            root.path("bossDrops").fields().forEachRemaining(entry -> bossDrops.put(
                    entry.getKey(), new BossSource(
                            entry.getValue().path("bossName").asText(),
                            entry.getValue().path("country").asText())));
            Map<String, String> weeklyBossDrops = new LinkedHashMap<>();
            root.path("weeklyBossDrops").fields().forEachRemaining(entry ->
                    weeklyBossDrops.put(entry.getKey(), entry.getValue().asText()));
            Map<String, String> aliases = new LinkedHashMap<>();
            root.path("monsterRouteAliases").fields().forEachRemaining(entry ->
                    aliases.put(entry.getKey(), entry.getValue().asText()));
            return new CatalogData(Map.copyOf(bossDrops), Map.copyOf(weeklyBossDrops), Map.copyOf(aliases));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取养成材料来源目录", exception);
        }
    }

    public record BossSource(String bossName, String country) {
    }

    public record MonsterSource(String routeFamily,
                                List<String> monsters,
                                List<String> availableRouteFamilies) {
    }

    private record CatalogData(Map<String, BossSource> bossDrops,
                               Map<String, String> weeklyBossDrops,
                               Map<String, String> monsterRouteAliases) {
    }
}
