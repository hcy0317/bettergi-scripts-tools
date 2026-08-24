package com.cloud_guest.cultivation.execution;

import java.util.List;

public record CultivationOneStopResult(
        String uid,
        int revision,
        String scriptGroupName,
        String scriptGroupFile,
        int autoPlanActions,
        int scriptTasks,
        String backupDirectory,
        List<String> warnings,
        String message
) {
}
