package com.cloud_guest.artifact.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;

/** Physical game identities are available even before a simulator implements a character. */
public final class OptimizationInventoryCatalog {
    private static final long TRAVELER_ID = 20000000L;
    private static final long UNKNOWN_ID = 99999999L;
    private OptimizationInventoryCatalog() {}

    public static ObjectNode attach(JsonNode engineCatalog, JsonNode nativeCatalog) {
        if (!engineCatalog.isObject() || !nativeCatalog.isArray())
            throw new IllegalArgumentException("角色身份目录格式无效");
        ObjectNode result = engineCatalog.deepCopy();
        var inventory = result.putArray("inventoryCharacters");
        var seen = new HashSet<Long>();
        for (JsonNode nativeCharacter : nativeCatalog) {
            long id = nativeCharacter.path("id").asLong(-1);
            // BetterGI's OCR fallback is not a verified person or an inventory owner.
            if (id == UNKNOWN_ID) continue;
            String name = nativeCharacter.path("name").asText();
            if (id <= 0 || name.isBlank() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("角色身份目录包含无效游戏ID或名称");
            if (!seen.add(id)) throw new IllegalArgumentException("角色身份目录包含重复游戏ID");
            String key = "inventory" + id;
            var identity = inventory.addObject().put("id", id).put("key", key).put("nativeName", name);
            var aliases = identity.putArray("inventoryAliases");
            aliases.add(name);
            String english = nativeCharacter.path("nameEn").asText();
            if (!english.isBlank()) aliases.add(english);
            for (JsonNode alias : nativeCharacter.path("alias"))
                if (alias.isTextual() && !alias.asText().isBlank() && alias.asText().length() <= 100
                    && !alias.asText().chars().anyMatch(Character::isISOControl)) aliases.add(alias.asText());
            var simulationKeys = identity.putArray("simulationKeys");
            for (JsonNode character : result.path("characters")) {
                long engineId=character.path("id").asLong(-2);
                boolean traveler=id==TRAVELER_ID&&(engineId==10000005L||engineId==10000007L);
                if (engineId != id && !traveler) continue;
                simulationKeys.add(character.path("key").asText());
                ObjectNode row = (ObjectNode) character;
                row.put("nativeName", name).put("inventoryKey", key).put("inventoryId",id);
                row.set("inventoryAliases", aliases.deepCopy());
                if (character.hasNonNull("icon_name") && !identity.has("icon_name"))
                    identity.set("icon_name", character.get("icon_name").deepCopy());
            }
            // This flag says only that an entry exists, never that every mechanic is complete.
            identity.put("hasSimulationEntry", !simulationKeys.isEmpty());
        }
        return result;
    }
}
