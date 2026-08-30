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
        String craftMaterialType,
        String craftCountry,
        AutoPlan plan
) {
    public CultivationNextActionResponse(
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
            AutoPlan plan) {
        this(status, message, executionMode, uid, revision, actionId, leaseExpiresAt, actionType,
                materialName, remaining, batchLimit, reconcileGrid, null, null, plan);
    }
}
