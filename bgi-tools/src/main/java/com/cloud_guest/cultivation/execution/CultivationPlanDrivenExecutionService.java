package com.cloud_guest.cultivation.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloud_guest.cultivation.CultivationUid;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.entitys.common.auto_plan.AutoBoss;
import com.cloud_guest.entitys.common.auto_plan.AutoDomain;
import com.cloud_guest.entitys.common.auto_plan.AutoPlan;
import com.cloud_guest.entitys.common.auto_plan.Physical;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CultivationPlanDrivenExecutionService {
    private static final String LEASED = "LEASED";
    private static final String AWAITING_RECONCILE = "AWAITING_RECONCILE";
    private static final String RECONCILE_RETRY_LEASED = "RECONCILE_RETRY_LEASED";
    private static final String COMPLETED = "COMPLETED";
    private static final String INVENTORY_RECONCILE_BATCH = "INVENTORY_RECONCILE_BATCH";
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

    public CultivationNextActionResponse claim(String uid, String executorId) {
        String normalizedUid = CultivationUid.normalize(uid);
        String normalizedExecutor = require(executorId, "执行器 ID");
        CultivationExecutionProjection projection = executionService.projection(normalizedUid);
        if (projection == null) return status("NO_PLAN", "该 UID 尚未建立养成账本", normalizedUid, 0);
        CultivationExecutionActionEntity existing = actionMapper.findLeased(normalizedUid, projection.revision());
        if (existing != null && AWAITING_RECONCILE.equals(existing.getStatus())) {
            if (INVENTORY_RECONCILE_BATCH.equals(existing.getActionType())) {
                return status("PLAN_NEEDS_RECONCILE", "组末库存存在未知值，需先重新完整清点",
                        normalizedUid, projection.revision());
            }
            String previousExecutor = existing.getExecutorId();
            existing.setExecutorId(normalizedExecutor);
            existing.setLeaseExpiresAt(LocalDateTime.now(clock).plus(LEASE_DURATION));
            int transferred = actionMapper.update(existing, Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                    .eq(CultivationExecutionActionEntity::getId, existing.getId())
                    .eq(CultivationExecutionActionEntity::getStatus, AWAITING_RECONCILE)
                    .eq(CultivationExecutionActionEntity::getExecutorId, previousExecutor)
                    .eq(CultivationExecutionActionEntity::getResultIdempotencyKey,
                            existing.getResultIdempotencyKey()));
            if (transferred != 1) {
                return status("BUSY", "行动对账租约刚被其他执行器接管或完成，请重新领取",
                        normalizedUid, projection.revision());
            }
            return fromEntity(existing, "NEEDS_RECONCILE", "上一行动缺少有效背包证据，停止消耗资源");
        }
        if ("COMPLETED".equals(projection.state())) {
            return status("COMPLETED", "当前养成计划已由权威库存确认完成", normalizedUid, projection.revision());
        }
        if ("NEEDS_RECONCILE".equals(projection.state())) {
            return status("PLAN_NEEDS_RECONCILE", "计划库存状态尚未闭合，请完成一次完整库存复核",
                    normalizedUid, projection.revision());
        }
        if ("NEEDS_CRAFT".equals(projection.state())) {
            if (projection.craftingActions().isEmpty()) {
                return status("PLAN_NEEDS_CRAFT", "存在可合成材料但尚未生成安全合成行动",
                        normalizedUid, projection.revision());
            }
        }

        if (existing != null) {
            if (existing.getLeaseExpiresAt() != null
                    && existing.getLeaseExpiresAt().isAfter(LocalDateTime.now(clock))) {
                if (INVENTORY_RECONCILE_BATCH.equals(existing.getActionType())) {
                    return status("BUSY", "该 UID 正在执行组末库存复核", normalizedUid, projection.revision());
                }
                if (!normalizedExecutor.equals(existing.getExecutorId())) {
                    return fromEntity(existing, "BUSY", "该 UID 已有其他执行器持有行动租约");
                }
                return fromEntity(existing, "ACTION", "恢复尚未到期的当前行动");
            }
            existing.setStatus("EXPIRED");
            existing.setLeaseKey(null);
            actionMapper.updateById(existing);
        }

        if (!projection.craftingActions().isEmpty()
                && !hasFreshCraftInventoryEvidence(projection)) {
            return status(
                    "PLAN_NEEDS_RECONCILE",
                    "材料合成前必须重新清点同族全部层级库存",
                    normalizedUid,
                    projection.revision());
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
        if (plan != null) plan.setId(actionId);
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
        entity.setPlanJson("CRAFT".equals(candidate.actionType())
                ? write(new CraftPayload(candidate.craftMaterialType(), candidate.craftCountry(), candidate.batchLimit()))
                : write(plan));
        try {
            actionMapper.insert(entity);
            return fromEntity(entity, "ACTION", candidate.reason());
        } catch (DuplicateKeyException conflict) {
            CultivationExecutionActionEntity winner = actionMapper.findLeased(normalizedUid, projection.revision());
            if (winner == null) throw conflict;
            if (INVENTORY_RECONCILE_BATCH.equals(winner.getActionType())) {
                return status("BUSY", "该 UID 正在执行组末库存复核", normalizedUid, projection.revision());
            }
            if (normalizedExecutor.equals(winner.getExecutorId())) {
                return fromEntity(winner, "ACTION", "并发领取已恢复数据库中的获胜行动");
            }
            return fromEntity(winner, "BUSY", "并发领取已由另一执行器成功");
        }
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
                LocalDateTime now = LocalDateTime.now(clock);
                if (entity.getLeaseExpiresAt() == null || !entity.getLeaseExpiresAt().isAfter(now)) {
                    throw new IllegalStateException("行动对账租约已过期");
                }
                entity.setObservedOwned(request.observedOwned());
                entity.setStatus(COMPLETED);
                entity.setLeaseKey(null);
                int updated = actionMapper.update(entity, Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                        .eq(CultivationExecutionActionEntity::getId, normalizedActionId)
                        .eq(CultivationExecutionActionEntity::getStatus, AWAITING_RECONCILE)
                        .eq(CultivationExecutionActionEntity::getExecutorId, executorId)
                        .eq(CultivationExecutionActionEntity::getResultIdempotencyKey, idempotencyKey)
                        .gt(CultivationExecutionActionEntity::getLeaseExpiresAt, now));
                if (updated != 1) return existingResultOrThrow(normalizedActionId, idempotencyKey);
            }
            return result(entity);
        }

        if (!LEASED.equals(entity.getStatus())) {
            throw new IllegalStateException("行动不再处于可提交的租约状态");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (entity.getLeaseExpiresAt() == null || !entity.getLeaseExpiresAt().isAfter(now)) {
            throw new IllegalStateException("行动租约已过期");
        }

        entity.setResultIdempotencyKey(idempotencyKey);
        entity.setObservedOwned(request.observedOwned());
        entity.setRewardsJson(write(request.rewards()));
        entity.setTerminationReason(request.terminationReason());
        if (request.observedOwned() != null && request.observedOwned() >= 0) {
            entity.setStatus(COMPLETED);
            entity.setLeaseKey(null);
        } else {
            entity.setStatus(AWAITING_RECONCILE);
        }
        int updated = actionMapper.update(entity, Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                .eq(CultivationExecutionActionEntity::getId, normalizedActionId)
                .eq(CultivationExecutionActionEntity::getStatus, LEASED)
                .eq(CultivationExecutionActionEntity::getExecutorId, executorId)
                .isNull(CultivationExecutionActionEntity::getResultIdempotencyKey)
                .gt(CultivationExecutionActionEntity::getLeaseExpiresAt, now));
        if (updated != 1) return existingResultOrThrow(normalizedActionId, idempotencyKey);
        return result(entity);
    }

    private CultivationActionResultResponse existingResultOrThrow(String actionId, String idempotencyKey) {
        CultivationExecutionActionEntity existing = actionMapper.selectById(actionId);
        if (existing != null && idempotencyKey.equals(existing.getResultIdempotencyKey())) {
            return result(existing);
        }
        throw new IllegalStateException("行动已由另一个幂等结果完成");
    }

    public CultivationInventoryReconcileTargetsResponse claimInventoryReconcile(
            String uid, String executorId) {
        String normalizedUid = CultivationUid.normalize(uid);
        String normalizedExecutor = require(executorId, "执行器 ID");
        CultivationExecutionProjection projection = executionService.projection(normalizedUid);
        if (projection == null) {
            return inventoryStatus("NO_PLAN", "该 UID 尚未建立养成账本", normalizedUid, 0, List.of());
        }
        Map<String, List<String>> materialNamesByGrid = executionService.inventoryReconcileTargets(normalizedUid);
        if (materialNamesByGrid == null || materialNamesByGrid.isEmpty()) {
            materialNamesByGrid = legacyReconcileTargets(projection);
        }
        List<String> materialNames = flattenReconcileTargets(materialNamesByGrid);
        if (materialNames.isEmpty()) {
            return inventoryStatus(
                    "NO_TARGETS", "当前没有需要组末复核的地方特产或怪物材料",
                    normalizedUid, projection.revision(), materialNames);
        }

        CultivationExecutionActionEntity existing = actionMapper.findLeased(normalizedUid, projection.revision());
        if (existing != null) {
            boolean inventoryBatch = INVENTORY_RECONCILE_BATCH.equals(existing.getActionType());
            boolean activeLease = existing.getLeaseExpiresAt() != null
                    && existing.getLeaseExpiresAt().isAfter(LocalDateTime.now(clock));
            if (!inventoryBatch && (AWAITING_RECONCILE.equals(existing.getStatus()) || activeLease)) {
                return new CultivationInventoryReconcileTargetsResponse(
                        "BUSY", "该 UID 仍有未完成的养成行动",
                        normalizedUid, projection.revision(), existing.getId(),
                        existing.getLeaseExpiresAt(), materialNames);
            }
            if (inventoryBatch && AWAITING_RECONCILE.equals(existing.getStatus())) {
                String previousExecutor = existing.getExecutorId();
                existing.setExecutorId(normalizedExecutor);
                existing.setLeaseExpiresAt(LocalDateTime.now(clock).plus(LEASE_DURATION));
                existing.setLeaseKey(normalizedUid + ":" + projection.revision());
                existing.setStatus(RECONCILE_RETRY_LEASED);
                int transferred = actionMapper.update(existing, Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                        .eq(CultivationExecutionActionEntity::getId, existing.getId())
                        .eq(CultivationExecutionActionEntity::getStatus, AWAITING_RECONCILE)
                        .eq(CultivationExecutionActionEntity::getExecutorId, previousExecutor)
                        .eq(CultivationExecutionActionEntity::getResultIdempotencyKey,
                                existing.getResultIdempotencyKey()));
                if (transferred != 1) {
                    return inventoryStatus(
                            "BUSY", "组末复核租约刚被其他执行器接管或完成，请重新领取",
                            normalizedUid, projection.revision(), materialNames);
                }
                return inventoryResponse(existing, "NEEDS_RECONCILE", "上次组末库存存在未知值，请重新完整清点",
                        materialNamesByGrid);
            }
            if (inventoryBatch && RECONCILE_RETRY_LEASED.equals(existing.getStatus())) {
                if (activeLease) {
                    if (!normalizedExecutor.equals(existing.getExecutorId())) {
                        return inventoryResponse(existing, "BUSY", "组末库存重试正由其他执行器持有",
                                materialNamesByGrid);
                    }
                    return inventoryResponse(existing, "NEEDS_RECONCILE", "恢复当前组末库存重试",
                            materialNamesByGrid);
                }
                String previousExecutor = existing.getExecutorId();
                existing.setExecutorId(normalizedExecutor);
                existing.setLeaseExpiresAt(LocalDateTime.now(clock).plus(LEASE_DURATION));
                int transferred = actionMapper.update(existing, Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                        .eq(CultivationExecutionActionEntity::getId, existing.getId())
                        .eq(CultivationExecutionActionEntity::getStatus, RECONCILE_RETRY_LEASED)
                        .eq(CultivationExecutionActionEntity::getExecutorId, previousExecutor)
                        .eq(CultivationExecutionActionEntity::getResultIdempotencyKey,
                                existing.getResultIdempotencyKey())
                        .le(CultivationExecutionActionEntity::getLeaseExpiresAt, LocalDateTime.now(clock)));
                if (transferred != 1) {
                    return inventoryStatus(
                            "BUSY", "组末库存重试租约刚被其他执行器接管，请重新领取",
                            normalizedUid, projection.revision(), materialNames);
                }
                return inventoryResponse(existing, "NEEDS_RECONCILE", "已接管过期的组末库存重试",
                        materialNamesByGrid);
            }
            if (activeLease) {
                if (!normalizedExecutor.equals(existing.getExecutorId())) {
                    return inventoryResponse(existing, "BUSY", "该 UID 已有其他执行器持有行动租约",
                            materialNamesByGrid);
                }
                return inventoryResponse(existing, "ACTION", "恢复尚未到期的组末库存复核",
                        materialNamesByGrid);
            }
            existing.setStatus("EXPIRED");
            existing.setLeaseKey(null);
            actionMapper.updateById(existing);
        }

        CultivationExecutionActionEntity entity = new CultivationExecutionActionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUid(normalizedUid);
        entity.setPlanRevision(projection.revision());
        entity.setExecutorId(normalizedExecutor);
        entity.setLeaseKey(normalizedUid + ":" + projection.revision());
        entity.setLeaseExpiresAt(LocalDateTime.now(clock).plus(LEASE_DURATION));
        entity.setStatus(LEASED);
        entity.setActionType(INVENTORY_RECONCILE_BATCH);
        entity.setMaterialName("__inventory_reconcile__");
        entity.setRemainingBefore(reconcileRemaining(projection).values().stream().mapToLong(Long::longValue).sum());
        entity.setPlanJson(write(materialNamesByGrid));
        try {
            actionMapper.insert(entity);
            return inventoryResponse(entity, "ACTION", "已领取组末权威库存复核租约", materialNamesByGrid);
        } catch (DuplicateKeyException conflict) {
            CultivationExecutionActionEntity winner = actionMapper.findLeased(normalizedUid, projection.revision());
            if (winner == null) throw conflict;
            if (INVENTORY_RECONCILE_BATCH.equals(winner.getActionType())) {
                return inventoryResponse(winner, "BUSY", "并发领取已由另一执行器成功", materialNamesByGrid);
            }
            return new CultivationInventoryReconcileTargetsResponse(
                    "BUSY", "并发领取时该 UID 已取得其他养成行动租约",
                    normalizedUid, projection.revision(), winner.getId(), winner.getLeaseExpiresAt(),
                    materialNames, materialNamesByGrid);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationInventoryObservationResponse recordInventoryObservations(
            String uid, CultivationInventoryObservationRequest request) {
        String normalizedUid = CultivationUid.normalize(uid);
        if (request == null) throw new IllegalArgumentException("库存观察不能为空");
        String actionId = require(request.actionId(), "复核行动 ID");
        String executorId = require(request.executorId(), "执行器 ID");
        String idempotencyKey = require(request.idempotencyKey(), "幂等键");
        if (!idempotencyKey.equals(actionId + ":result")) {
            throw new IllegalArgumentException("库存复核幂等键必须由服务端行动 ID 派生");
        }
        CultivationExecutionActionEntity entity = actionMapper.selectById(actionId);
        if (entity == null || !normalizedUid.equals(entity.getUid())
                || !INVENTORY_RECONCILE_BATCH.equals(entity.getActionType())) {
            throw new IllegalArgumentException("库存复核行动不存在或不属于该 UID");
        }
        if (!executorId.equals(entity.getExecutorId())) {
            throw new IllegalStateException("库存复核租约不属于当前执行器");
        }
        if (entity.getPlanRevision() == null || request.expectedRevision() != entity.getPlanRevision()) {
            throw new IllegalStateException("库存观察 revision 已过期");
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>(readInventoryTargets(entity));
        LinkedHashMap<String, Long> submitted = new LinkedHashMap<>();
        request.observedOwned().forEach((name, value) -> submitted.put(require(name, "材料名称"), value));
        if (!targets.containsAll(submitted.keySet())) {
            throw new IllegalArgumentException("库存观察包含当前组末复核目标之外的材料");
        }
        if (submitted.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("库存未知值必须使用负数显式上报");
        }

        if (entity.getResultIdempotencyKey() != null) {
            if (!idempotencyKey.equals(entity.getResultIdempotencyKey())) {
                throw new IllegalStateException("库存复核已由另一个幂等结果完成");
            }
            if (COMPLETED.equals(entity.getStatus())) {
                Map<String, Long> stored = readInventoryObservations(entity);
                if (!isCompatibleInventorySubmission(submitted, stored, targets)) {
                    throw new IllegalStateException("库存复核幂等键已对应不同结果");
                }
                return inventoryResult(entity, stored);
            }
            if (!AWAITING_RECONCILE.equals(entity.getStatus())
                    && !RECONCILE_RETRY_LEASED.equals(entity.getStatus())) {
                throw new IllegalStateException("库存复核不再处于可提交状态");
            }
        } else if (!LEASED.equals(entity.getStatus())) {
            throw new IllegalStateException("库存复核不再处于可提交的租约状态");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (entity.getLeaseExpiresAt() == null || !entity.getLeaseExpiresAt().isAfter(now)) {
            throw new IllegalStateException("库存复核租约已过期");
        }
        Map<String, Long> previousInventory = previousInventory(normalizedUid, entity.getPlanRevision());
        boolean hadUnknown = targets.stream().anyMatch(name ->
                !submitted.containsKey(name) || submitted.get(name) < 0);
        LinkedHashMap<String, Long> observations = new LinkedHashMap<>();
        targets.forEach(name -> {
            Long reported = submitted.get(name);
            observations.put(name, reported != null && reported >= 0
                    ? reported
                    : previousInventory.getOrDefault(name, -1L));
        });
        boolean hasUnknown = observations.values().stream().anyMatch(value -> value < 0);
        String previousStatus = entity.getStatus();
        entity.setResultIdempotencyKey(idempotencyKey);
        entity.setRewardsJson(write(observations));
        entity.setTerminationReason(hasUnknown
                ? "INVENTORY_RECONCILE_UNKNOWN_PRESERVED"
                : hadUnknown
                    ? "INVENTORY_RECONCILE_PARTIAL_WITH_PREVIOUS"
                    : "INVENTORY_RECONCILE");
        entity.setStatus(hasUnknown ? AWAITING_RECONCILE : COMPLETED);
        entity.setLeaseKey(hasUnknown ? entity.getUid() + ":" + entity.getPlanRevision() : null);
        var update = Wrappers.<CultivationExecutionActionEntity>lambdaUpdate()
                .eq(CultivationExecutionActionEntity::getId, actionId)
                .eq(CultivationExecutionActionEntity::getStatus, previousStatus)
                .eq(CultivationExecutionActionEntity::getExecutorId, executorId)
                .gt(CultivationExecutionActionEntity::getLeaseExpiresAt, now);
        if (previousStatus.equals(LEASED)) {
            update.isNull(CultivationExecutionActionEntity::getResultIdempotencyKey);
        } else {
            update.eq(CultivationExecutionActionEntity::getResultIdempotencyKey, idempotencyKey);
        }
        int updated = actionMapper.update(entity, update);
        if (updated != 1) {
            CultivationExecutionActionEntity winner = actionMapper.selectById(actionId);
            if (winner != null && idempotencyKey.equals(winner.getResultIdempotencyKey())
                    && observations.equals(readInventoryObservations(winner))) {
                return inventoryResult(winner, observations);
            }
            throw new IllegalStateException("库存复核已由另一个幂等结果完成");
        }
        return inventoryResult(entity, observations);
    }

    private Map<String, Long> previousInventory(String uid, int revision) {
        CultivationExecutionProjection projection = executionService.projection(uid);
        if (projection == null || projection.revision() != revision) return Map.of();

        LinkedHashMap<String, Long> previous = new LinkedHashMap<>();
        projection.materialProgress().forEach(progress ->
                putTrustedInventory(previous, progress.materialName(), progress.currentOwned()));
        projection.gatherAction().csvTargets().forEach(target ->
                putTrustedInventory(previous, target.materialName(), target.currentOwned()));
        projection.monsterAction().targets().forEach(target ->
                putTrustedInventory(previous, target.materialName(), target.currentOwned()));
        return previous;
    }

    private static void putTrustedInventory(Map<String, Long> inventory, String name, long value) {
        if (name != null && !name.isBlank() && value >= 0) inventory.putIfAbsent(name, value);
    }

    private static boolean isCompatibleInventorySubmission(
            Map<String, Long> submitted, Map<String, Long> stored, java.util.Set<String> targets) {
        if (!stored.keySet().equals(targets)) return false;
        return submitted.entrySet().stream().allMatch(entry ->
                entry.getValue() < 0 || java.util.Objects.equals(stored.get(entry.getKey()), entry.getValue()));
    }

    private boolean hasFreshCraftInventoryEvidence(CultivationExecutionProjection projection) {
        Map<String, List<String>> targets = executionService.inventoryReconcileTargets(projection.uid());
        List<String> craftingTargets = targets == null
                ? List.of()
                : targets.getOrDefault("CharacterDevelopmentItems", List.of());
        if (craftingTargets.isEmpty()) return false;
        List<CultivationExecutionActionEntity> observations = actionMapper.findCompletedObservations(
                projection.uid(), projection.revision());
        if (observations == null || observations.isEmpty()) return false;
        CultivationExecutionActionEntity latest = observations.getFirst();
        if (!INVENTORY_RECONCILE_BATCH.equals(latest.getActionType())) return false;
        Map<String, Long> inventory = readInventoryObservations(latest);
        return craftingTargets.stream().allMatch(name -> inventory.getOrDefault(name, -1L) >= 0);
    }

    private static Map<String, List<String>> legacyReconcileTargets(CultivationExecutionProjection projection) {
        Map<String, List<String>> targets = new LinkedHashMap<>();
        List<String> specialties = projection.gatherAction().csvTargets().stream()
                .map(CultivationExecutionProjection.GatherTarget::materialName).toList();
        List<String> materials = projection.monsterAction().targets().stream()
                .map(CultivationExecutionProjection.MonsterTarget::materialName).toList();
        if (!specialties.isEmpty()) targets.put("Materials", specialties);
        if (!materials.isEmpty()) targets.put("CharacterDevelopmentItems", materials);
        return targets;
    }

    private static List<String> flattenReconcileTargets(Map<String, List<String>> targets) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        List.of("Materials", "CharacterDevelopmentItems").forEach(grid ->
                targets.getOrDefault(grid, List.of()).forEach(names::add));
        targets.forEach((grid, values) -> values.forEach(names::add));
        return List.copyOf(names);
    }

    private static Map<String, Long> reconcileRemaining(CultivationExecutionProjection projection) {
        Map<String, Long> remaining = new java.util.LinkedHashMap<>();
        projection.gatherAction().csvTargets().forEach(target ->
                remaining.put(target.materialName(), target.remaining()));
        projection.monsterAction().targets().forEach(target ->
                remaining.put(target.materialName(), target.remaining()));
        return Map.copyOf(remaining);
    }

    private List<String> readInventoryTargets(CultivationExecutionActionEntity entity) {
        try {
            var json = objectMapper.readTree(entity.getPlanJson());
            if (json.isArray()) {
                return objectMapper.convertValue(json, new TypeReference<>() {});
            }
            Map<String, List<String>> grouped = objectMapper.convertValue(json, new TypeReference<>() {});
            return flattenReconcileTargets(grouped);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取组末库存复核目标", exception);
        }
    }

    private Map<String, List<String>> readInventoryTargetGroups(
            CultivationExecutionActionEntity entity,
            Map<String, List<String>> currentGroups) {
        try {
            var json = objectMapper.readTree(entity.getPlanJson());
            if (!json.isArray()) {
                Map<String, List<String>> grouped = objectMapper.convertValue(json, new TypeReference<>() {});
                return grouped.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
            }

            LinkedHashSet<String> frozen = new LinkedHashSet<>(
                    objectMapper.convertValue(json, new TypeReference<List<String>>() {}));
            LinkedHashSet<String> assigned = new LinkedHashSet<>();
            LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
            if (currentGroups != null) {
                currentGroups.forEach((grid, values) -> {
                    List<String> matching = values.stream()
                            .filter(frozen::contains)
                            .filter(assigned::add)
                            .toList();
                    if (!matching.isEmpty()) grouped.put(grid, matching);
                });
            }
            List<String> remaining = frozen.stream().filter(assigned::add).toList();
            if (!remaining.isEmpty()) {
                List<String> merged = new java.util.ArrayList<>(
                        grouped.getOrDefault("CharacterDevelopmentItems", List.of()));
                merged.addAll(remaining);
                grouped.put("CharacterDevelopmentItems", List.copyOf(merged));
            }
            return Map.copyOf(grouped);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取组末库存复核分组目标", exception);
        }
    }

    private Map<String, Long> readInventoryObservations(CultivationExecutionActionEntity entity) {
        if (entity.getRewardsJson() == null || entity.getRewardsJson().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(entity.getRewardsJson(), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取组末库存复核结果", exception);
        }
    }

    private CultivationInventoryObservationResponse inventoryResult(
            CultivationExecutionActionEntity entity, Map<String, Long> observations) {
        int observedCount = (int) observations.values().stream().filter(value -> value >= 0).count();
        boolean completed = COMPLETED.equals(entity.getStatus());
        boolean hasUnknown = observations.values().stream().anyMatch(value -> value < 0);
        boolean usedPrevious = "INVENTORY_RECONCILE_PARTIAL_WITH_PREVIOUS"
                .equals(entity.getTerminationReason());
        return new CultivationInventoryObservationResponse(
            completed ? "REPLANNING" : "NEEDS_RECONCILE",
                completed
                        ? usedPrevious
                            ? "已回写识别成功项，未知项沿用上次可信库存，将自动重生成后续路线"
                            : "权威库存已回写，将自动重生成后续路线"
                        : hasUnknown
                            ? "合成相关库存仍有未知项，已阻止继续领取行动"
                            : "库存复核尚未完成",
                entity.getUid(), entity.getPlanRevision(), observedCount);
    }

    private CultivationInventoryReconcileTargetsResponse inventoryResponse(
            CultivationExecutionActionEntity entity, String status, String message,
            Map<String, List<String>> materialNamesByGrid) {
        Map<String, List<String>> frozenGroups = readInventoryTargetGroups(
                entity, materialNamesByGrid);
        return new CultivationInventoryReconcileTargetsResponse(
                status, message, entity.getUid(), entity.getPlanRevision(), entity.getId(),
                entity.getLeaseExpiresAt(), readInventoryTargets(entity), frozenGroups);
    }

    private static CultivationInventoryReconcileTargetsResponse inventoryStatus(
            String status, String message, String uid, int revision, List<String> materialNames) {
        return new CultivationInventoryReconcileTargetsResponse(
                status, message, uid, revision, null, null, materialNames);
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
        if (!projection.craftingActions().isEmpty()) {
            CultivationCraftingAction action = projection.craftingActions().getFirst();
            return new Candidate(
                    "CRAFT", action.materialName(), action.quantity(), (int) action.quantity(), null,
                    action.materialType(), executionService.craftingCountry(projection.uid()),
                    "低阶库存可按 3:1 合成为当前缺口；合成并复核前不再消耗树脂");
        }
        int today = LocalDate.now(clock).getDayOfWeek().getValue() % 7;
        List<String> configuredResinPriority = executionService.resinPriority(projection.uid());
        List<String> resinPriority = configuredResinPriority == null
                ? List.of("浓缩树脂", "原粹树脂")
                : configuredResinPriority;
        Candidate domain = projection.resinActions().stream()
                .filter(action -> "秘境".equals(action.actionType()))
                .filter(action -> action.availableDays().isEmpty() || action.availableDays().contains(today))
                .filter(action -> !"已暂停".equals(action.actionState()))
                .sorted(Comparator.comparingLong(CultivationExecutionProjection.ResinAction::remaining))
                .map(action -> domain(action, resinPriority)).findFirst().orElse(null);
        if (domain != null) return domain;

        return projection.bossActions().stream()
                .filter(action -> !"已暂停".equals(action.actionState()))
                .sorted(Comparator.comparingLong(CultivationExecutionProjection.BossAction::remaining))
                .map(this::boss).findFirst().orElse(null);
    }

    private Candidate domain(CultivationExecutionProjection.ResinAction action,
                             List<String> resinPriority) {
        AutoPlan plan = basePlan(action.availableDays(), action.sourceType(), "秘境");
        plan.setAutoDomain(new AutoDomain(
                action.sourceName(), action.sourceMaterialIndex(), action.sourceMaterialName(),
                action.partyName(), 1, physical(resinPriority)));
        return new Candidate("DOMAIN", action.materialName(), action.remaining(), 1, plan, null, null,
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
        return new Candidate("WORLD_BOSS", action.materialName(), action.remaining(), 1, plan, null, null,
                "按突破材料缺口发放一个世界首领安全批次，完成后强制清点并重规划");
    }

    private CultivationNextActionResponse fromEntity(CultivationExecutionActionEntity entity,
                                                      String status, String message) {
        AutoPlan plan = null;
        CraftPayload craft = null;
        if ("CRAFT".equals(entity.getActionType())) {
            try {
                craft = objectMapper.readValue(entity.getPlanJson(), CraftPayload.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("无法恢复材料合成行动", exception);
            }
        } else if (entity.getPlanJson() != null) {
            try {
                plan = objectMapper.readValue(entity.getPlanJson(), AutoPlan.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("无法恢复行动计划", exception);
            }
        }
        return new CultivationNextActionResponse(
                status, message, "PLAN_DRIVEN", entity.getUid(), entity.getPlanRevision(), entity.getId(),
                entity.getLeaseExpiresAt(), entity.getActionType(), entity.getMaterialName(),
                entity.getRemainingBefore() == null ? 0 : entity.getRemainingBefore(),
                craft == null ? 1 : craft.quantity(),
                "CharacterDevelopmentItems",
                craft == null ? null : craft.materialType(),
                craft == null ? null : craft.country(),
                plan);
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

    private static List<Physical> physical(List<String> resinPriority) {
        List<String> all = List.of("浓缩树脂", "原粹树脂", "须臾树脂", "脆弱树脂");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (resinPriority != null) {
            resinPriority.stream().filter(all::contains).forEach(selected::add);
        }
        List<String> ordered = new java.util.ArrayList<>(selected);
        all.stream().filter(name -> !selected.contains(name)).forEach(ordered::add);
        List<Physical> result = new java.util.ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            String name = ordered.get(index);
            boolean enabled = selected.contains(name);
            result.add(new Physical(index, name, enabled, enabled ? 1 : 0));
        }
        return List.copyOf(result);
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

    private record Candidate(String actionType, String materialName, long remaining, int batchLimit,
                             AutoPlan plan, String craftMaterialType, String craftCountry, String reason) {
    }

    private record CraftPayload(String materialType, String country, int quantity) {
    }
}
