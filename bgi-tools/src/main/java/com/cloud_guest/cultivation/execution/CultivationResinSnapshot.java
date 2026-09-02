package com.cloud_guest.cultivation.execution;

import java.util.List;

public record CultivationResinSnapshot(
        int originalResinCount,
        int condensedResinCount,
        int transientResinCount,
        int fragileResinCount
) {
    public boolean hasUsableResin(List<String> priority) {
        if (priority == null || priority.isEmpty()) return false;
        return priority.stream().anyMatch(name -> switch (name) {
            case "原粹树脂" -> originalResinCount >= 20;
            case "浓缩树脂" -> condensedResinCount > 0;
            case "须臾树脂" -> transientResinCount > 0;
            case "脆弱树脂" -> fragileResinCount > 0;
            default -> false;
        });
    }
}
