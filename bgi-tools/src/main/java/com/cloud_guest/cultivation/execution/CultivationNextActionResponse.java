package com.cloud_guest.cultivation.execution;

import com.cloud_guest.entitys.common.auto_plan.AutoPlan;

import java.time.LocalDateTime;

public record CultivationNextActionResponse(
        String status,
        String message,
        String executionMode,
        String uid,
        int revision,
        String actionId,
        LocalDateTime leaseExpiresAt,
        String actionType,
        String materialName,
        long remaining,
        int batchLimit,
        String reconcileGrid,
        AutoPlan plan
) {
}
