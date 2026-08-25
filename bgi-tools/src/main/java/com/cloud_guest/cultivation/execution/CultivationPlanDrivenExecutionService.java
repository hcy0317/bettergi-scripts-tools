package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.entitys.common.auto_plan.AutoBoss;
import com.cloud_guest.entitys.common.auto_plan.AutoDomain;
import com.cloud_guest.entitys.common.auto_plan.AutoPlan;
import com.cloud_guest.entitys.common.auto_plan.Physical;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CultivationPlanDrivenExecutionService {
    private static final String LEASED = "LEASED";
    private static final String AWAITING_RECONCILE = "AWAITING_RECONCILE";
    private static final String COMPLETED = "COMPLETED";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(45);
    private static final Duration NO_PROGRESS_COOLDOWN = Duration.ofMinutes(30);

    private final CultivationExecutionService executionService;
    private final CultivationExecutionActionMapper actionMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CultivationPlanDrivenExecutionService(CultivationExecutionService executionService,
                                                 CultivationExecutionActionMapper actionMapper,
                                                 ObjectMapper objectMapper) {
        this(executionService, actionMapper, objectMapper, Clock.systemDefaultZone());
    }

    CultivationPlanDrivenExecutionService(CultivationExecutionService executionService,
                                          CultivationExecutionActionMapper actionMapper,
                                          ObjectMapper objectMapper,
                                          Clock clock) {
        this.executionService = executionService;
        this.actionMapper = actionMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationNextActionResponse claim(String uid, String executorId) {
        String normalizedUid = require(uid, "UID");
        String normalizedExecutor = require(executorId, "执行器 ID");
        CultivationExecutionProjection projection = executionService.projection(normalizedUid);
        if (projection == null) return status("NO_PLAN", "该 UID 尚未建立养成账本", normalizedUid, 0);
        if ("COMPLETED".equals(projection.state())) {
            return status("COMPLETED", "当前养成计划已由权威库存确认完成", normalizedUid, projection.revision());
        }
        if ("NEEDS_RECONCILE".equals(projection.state())) {
            return status("PLAN_NEEDS_RECONCILE", "库存低于导入基线，需重新导入或人工确认后再执行",
                    normalizedUid, projection.revision());
        }
        if ("NEEDS_CRAFT".equals(projection.state())) {
            return status("PLAN_NEEDS_CRAFT", "实际低阶奖励已足够合成目标材料；完成合成闭环前不再消耗树脂",
                    normalizedUid, projection.revision());
        }

        CultivationExecutionActionEntity existing = actionMapper.findLeased(normalizedUid, projection.revision());
        if (existing != null) {
            if (AWAITING_RECONCILE.equals(existing.getStatus())) {
                existing.setExecutorId(normalizedExecutor);
                actionMapper.updateById(existing);
                return fromEntity(existing, "NEEDS_RECONCILE", "上一行动缺少有效背包证据，停止消耗资源");
            }
            if (existing.getLeaseExpiresAt() != null
                    && existing.getLeaseExpiresAt().isAfter(LocalDateTime.now(clock))) {
                if (!normalizedExecutor.equals(existing.getExecutorId())) {
                    return fromEntity(existing, "BUSY", "该 UID 已有其他执行器持有行动租约");
                }
                return fromEntity(existing, "ACTION", "恢复尚未到期的当前行动");
            }
            existing.setStatus("EXPIRED");
            existing.setLeaseKey(null);
            actionMapper.updateById(existing);
        }

        Candidate candidate = choose(projection);
        if (candidate == null) {
            return status("WAITING", "当前没有满足开放日和 P1 证据要求的体力行动", normalizedUid,
                    projection.revision());
        }
        if (recentlyMadeNoProgress(normalizedUid, projection.revision(), candidate)) {
            return status(
                    "WAITING",
                    "上一相同行动未产生奖励或库存进展，30 分钟内不再重复消耗时间",
                    normalizedUid,
                    projection.revision());
        }

        String actionId = UUID.randomUUID().toString();
        AutoPlan plan = candidate.plan();
        plan.setId(actionId);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(LEASE_DURATION);
        CultivationExecutionActionEntity entity = new CultivationExecutionActionEntity();
        entity.setId(actionId);
        entity.setUid(normalizedUid);
        entity.setPlanRevision(projection.revision());
        entity.setExecutorId(normalizedExecutor);
        entity.setLeaseKey(normalizedUid + ":" + projection.revision());
        entity.setLeaseExpiresAt(expiresAt);
        entity.setStatus(LEASED);
        entity.setActionType(candidate.actionType());
        entity.setMaterialName(candidate.materialName());
        entity.setRemainingBefore(candidate.remaining());
        entity.setPlanJson(write(plan));
        actionMapper.insert(entity);
        return fromEntity(entity, "ACTION", candidate.reason());
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationActionResultResponse complete(String actionId, CultivationActionResultRequest request) {
        String normalizedActionId = require(actionId, "行动 ID");
        if (request == null) throw new IllegalArgumentException("行动结果不能为空");
        String executorId = require(request.executorId(), "执行器 ID");
        String idempotencyKey = require(request.idempotencyKey(), "幂等键");
        CultivationExecutionActionEntity entity = actionMapper.selectById(normalizedActionId);
        if (entity == null) throw new IllegalArgumentException("行动不存在或已失效");
        if (!executorId.equals(entity.getExecutorId())) throw new IllegalStateException("行动租约不属于当前执行器");
        if (request.expectedRevision() != entity.getPlanRevision()) {
            throw new IllegalStateException("行动结果 revision 已过期");
        }
        if (entity.getResultIdempotencyKey() != null) {
            if (!idempotencyKey.equals(entity.getResultIdempotencyKey())) {
                throw new IllegalStateException("行动已由另一个幂等结果完成");
            }
            if (AWAITING_RECONCILE.equals(entity.getStatus())
                    && request.observedOwned() != null && request.observedOwned() >= 0) {
                entity.setObservedOwned(request.observedOwned());
                entity.setRewardsJson(write(request.rewards()));
                entity.setTerminationReason(request.terminationReason());
                entity.setStatus(COMPLETED);
                actionMapper.updateById(entity);
            }
            return result(entity);
        }

        entity.setResultIdempotencyKey(idempotencyKey);
        entity.setObservedOwned(request.observedOwned());
        entity.setRewardsJson(write(request.rewards()));
        entity.setTerminationReason(request.terminationReason());
        entity.setLeaseKey(null);
        if (request.observedOwned() != null && request.observedOwned() >= 0) {
            entity.setStatus(COMPLETED);
        } else {
            entity.setStatus(AWAITING_RECONCILE);
        }
        actionMapper.updateById(entity);
        return result(entity);
    }

    private CultivationActionResultResponse result(CultivationExecutionActionEntity entity) {
        boolean completed = COMPLETED.equals(entity.getStatus());
        boolean noProgress = completed
                && entity.getTerminationReason() != null
                && entity.getTerminationReason().startsWith("NO_PROGRESS:");
        return new CultivationActionResultResponse(
                noProgress ? "STOPPED_NO_PROGRESS" : completed ? "REPLANNING" : "NEEDS_RECONCILE",
                noProgress
                        ? "行动未产生奖励或库存进展，停止本轮计划驱动执行"
                        : completed ? "权威库存已回写，将重新选择下一行动" : "库存识别未知，停止并等待重新清点",
                entity.getUid(), entity.getId(), entity.getPlanRevision(), entity.getMaterialName(),
                entity.getObservedOwned());
    }

    private boolean recentlyMadeNoProgress(String uid, int revision, Candidate candidate) {
        List<CultivationExecutionActionEntity> observations = actionMapper.findCompletedObservations(uid, revision);
        if (observations == null || observations.isEmpty()) return false;
        CultivationExecutionActionEntity latest = observations.get(0);
        LocalDateTime updatedAt = latest.getUpdateTime() != null ? latest.getUpdateTime() : latest.getCreateTime();
        if (updatedAt == null || updatedAt.isBefore(LocalDateTime.now(clock).minus(NO_PROGRESS_COOLDOWN))) {
            return false;
        }
        if (!candidate.actionType().equals(latest.getActionType())
                || !candidate.materialName().equals(latest.getMaterialName())
                || latest.getRemainingBefore() == null
                || latest.getRemainingBefore() != candidate.remaining()) {
            return false;
        }
        try {
            var rewards = latest.getRewardsJson() == null
                    ? null : objectMapper.readTree(latest.getRewardsJson());
            return rewards == null || !rewards.isObject() || rewards.isEmpty();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private Candidate choose(CultivationExecutionProjection projection) {
        int today = LocalDate.now(clock).getDayOfWeek().getValue() % 7;
        Candidate domain = projection.resinActions().stream()
                .filter(action -> "秘境".equals(action.actionType()))
                .filter(action -> action.availableDays().isEmpty() || action.availableDays().contains(today))
                .filter(action -> !"已暂停".equals(action.actionState()))
                .sorted(Comparator.comparingLong(CultivationExecutionProjection.ResinAction::remaining))
                .map(this::domain).findFirst().orElse(null);
        if (domain != null) return domain;

        return projection.bossActions().stream()
                .filter(action -> !"已暂停".equals(action.actionState()))
                .sorted(Comparator.comparingLong(CultivationExecutionProjection.BossAction::remaining))
                .map(this::boss).findFirst().orElse(null);
    }

    private Candidate domain(CultivationExecutionProjection.ResinAction action) {
        AutoPlan plan = basePlan(action.availableDays(), action.sourceType(), "秘境");
        plan.setAutoDomain(new AutoDomain(
                action.sourceName(), action.sourceMaterialIndex(), action.sourceMaterialName(),
                action.partyName(), 1, defaultPhysical()));
        return new Candidate("DOMAIN", action.materialName(), action.remaining(), plan,
                "今天开放该材料秘境；按账本缺口发放一个安全批次，完成后强制清点并重规划");
    }

    private Candidate boss(CultivationExecutionProjection.BossAction action) {
        Map<String, Object> settings = action.settings();
        AutoPlan plan = basePlan(List.of(), "首领", "Boss");
        plan.setAutoBoss(new AutoBoss(
                action.bossName(), string(settings, "bossStrategyName", "根据队伍自动选择"), "",
                action.partyName(), true, 1, false, false,
                integer(settings, "bossReviveRetryCount", 5),
                bool(settings, "bossReturnToStatueAfterEachRound", false),
                bool(settings, "bossRewardRecognitionEnabled", true),
                integer(settings, "bossTimeoutSeconds", 300)));
        return new Candidate("WORLD_BOSS", action.materialName(), action.remaining(), plan,
                "按突破材料缺口发放一个世界首领安全批次，完成后强制清点并重规划");
    }

    private CultivationNextActionResponse fromEntity(CultivationExecutionActionEntity entity,
                                                      String status, String message) {
        AutoPlan plan = null;
        if (entity.getPlanJson() != null) {
            try {
                plan = objectMapper.readValue(entity.getPlanJson(), AutoPlan.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("无法恢复行动计划", exception);
            }
        }
        return new CultivationNextActionResponse(
                status, message, "PLAN_DRIVEN", entity.getUid(), entity.getPlanRevision(), entity.getId(),
                entity.getLeaseExpiresAt(), entity.getActionType(), entity.getMaterialName(),
                entity.getRemainingBefore() == null ? 0 : entity.getRemainingBefore(), 1,
                "Materials", plan);
    }

    private static CultivationNextActionResponse status(String status, String message,
                                                        String uid, int revision) {
        return new CultivationNextActionResponse(
                status, message, "PLAN_DRIVEN", uid, revision, null, null,
                null, null, 0, 0, null, null);
    }

    private static AutoPlan basePlan(List<Integer> days, String selectedType, String runType) {
        return new AutoPlan()
                .setOrder(1)
                .setDays(days)
                .setDayName(days.isEmpty() ? "" : "由计划器按开放日发放")
                .setSelectedType(selectedType)
                .setRunType(runType)
                .setEnable(true)
                .setCultivate(true)
                .setRecord(false);
    }

    private static List<Physical> defaultPhysical() {
        return List.of(
                new Physical(0, "浓缩树脂", true, 1),
                new Physical(1, "原粹树脂", true, 1),
                new Physical(2, "须臾树脂", false, 0),
                new Physical(3, "脆弱树脂", false, 0));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化养成行动", exception);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }

    private static String string(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static int integer(Map<String, Object> settings, String key, int fallback) {
        Object value = settings.get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private record Candidate(String actionType, String materialName, long remaining,
                             AutoPlan plan, String reason) {
    }
}
