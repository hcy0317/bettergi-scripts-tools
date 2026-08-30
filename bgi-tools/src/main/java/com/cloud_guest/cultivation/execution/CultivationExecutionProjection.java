package com.cloud_guest.cultivation.execution;

import java.util.List;
import java.util.Map;

public record CultivationExecutionProjection(
        String uid,
        int revision,
        String state,
        String executionMode,
        List<CultivationCraftingAction> craftingActions,
        List<ResinAction> resinActions,
        List<BossAction> bossActions,
        List<WeeklyBossAction> weeklyBossActions,
        GatherAction gatherAction,
        MonsterAction monsterAction,
        List<PendingMaterial> pendingMaterials,
        CultivationExecutionPreferences preferences,
        List<String> partyOptions,
        List<String> combatStrategyOptions,
        List<MaterialProgress> materialProgress
) {
    public CultivationExecutionProjection(
            String uid,
            int revision,
            String state,
            String executionMode,
            List<CultivationCraftingAction> craftingActions,
            List<ResinAction> resinActions,
            List<BossAction> bossActions,
            List<WeeklyBossAction> weeklyBossActions,
            GatherAction gatherAction,
            MonsterAction monsterAction,
            List<PendingMaterial> pendingMaterials,
            CultivationExecutionPreferences preferences,
            List<String> partyOptions) {
        this(uid, revision, state, executionMode, craftingActions, resinActions, bossActions, weeklyBossActions,
                gatherAction, monsterAction, pendingMaterials, preferences, partyOptions, List.of(), List.of());
    }

    public CultivationExecutionProjection(
            String uid,
            int revision,
            String state,
            String executionMode,
            List<ResinAction> resinActions,
            List<BossAction> bossActions,
            List<WeeklyBossAction> weeklyBossActions,
            GatherAction gatherAction,
            MonsterAction monsterAction,
            List<PendingMaterial> pendingMaterials,
            CultivationExecutionPreferences preferences,
            List<String> partyOptions) {
        this(uid, revision, state, executionMode, List.of(), resinActions, bossActions, weeklyBossActions,
                gatherAction, monsterAction, pendingMaterials, preferences, partyOptions, List.of(), List.of());
    }

    public record MaterialProgress(
            String materialName,
            long currentOwned,
            long required,
            long remaining,
            String familyName,
            int tierIndex,
            int tierCount,
            int qualityLevel
    ) {
    }

    public record ResinAction(
            String materialName,
            long remaining,
            String actionType,
            String sourceName,
            String sourceType,
            String partyName,
            String actionState,
            Integer sourceMaterialIndex,
            String sourceMaterialName,
            List<Integer> availableDays
    ) {
    }

    public record WeeklyBossAction(
            String materialName,
            long remaining,
            String bossName,
            Map<String, Object> settings,
            String actionState
    ) {
    }

    public record BossAction(
            String materialName,
            long remaining,
            String bossName,
            String country,
            String partyName,
            Map<String, Object> settings,
            String actionState
    ) {
    }

    public record GatherTarget(
            String materialName,
            long required,
            long baselineOwned,
            long currentOwned,
            long remaining,
            String country,
            String selectionKey
    ) {
    }

    public record GatherAction(
            String scriptName,
            String actionState,
            Map<String, Object> settings,
            List<GatherTarget> csvTargets
    ) {
    }

    public record MonsterTarget(
            String materialName,
            long required,
            long baselineOwned,
            long currentOwned,
            long remaining,
            String routeFamily,
            List<String> monsters
    ) {
    }

    public record MonsterAction(
            String scriptName,
            String actionState,
            Map<String, Object> settings,
            List<MonsterTarget> targets,
            List<String> availableRouteFamilies
    ) {
    }

    public record PendingMaterial(
            String materialName,
            long remaining,
            String reason
    ) {
    }
}
