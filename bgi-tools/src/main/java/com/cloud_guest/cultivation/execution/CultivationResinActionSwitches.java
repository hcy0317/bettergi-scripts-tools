package com.cloud_guest.cultivation.execution;

public record CultivationResinActionSwitches(
        boolean talentDomainEnabled,
        boolean weaponDomainEnabled,
        boolean moraLeyLineEnabled,
        boolean experienceLeyLineEnabled) {

    public static CultivationResinActionSwitches allEnabled() {
        return new CultivationResinActionSwitches(true, true, true, true);
    }

    public boolean allows(CultivationExecutionProjection.ResinAction action) {
        if ("天赋".equals(action.sourceType())) return talentDomainEnabled;
        if ("武器".equals(action.sourceType())) return weaponDomainEnabled;
        if ("摩拉".equals(action.materialName()) || "藏金之花".equals(action.sourceName())) {
            return moraLeyLineEnabled;
        }
        if ("大英雄的经验".equals(action.materialName()) || "启示之花".equals(action.sourceName())) {
            return experienceLeyLineEnabled;
        }
        return true;
    }
}
