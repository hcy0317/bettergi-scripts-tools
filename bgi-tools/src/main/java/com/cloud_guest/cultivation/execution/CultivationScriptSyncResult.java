package com.cloud_guest.cultivation.execution;

import java.util.List;

public record CultivationScriptSyncResult(
        String moduleId,
        int updatedTasks,
        List<String> scriptGroupFiles,
        String backupDirectory,
        String message
) {
}
