import {taskHandlerMap} from "./load_check_run";

function apiHeaders(token) {
    const headers = {"Content-Type": "application/json"};
    const name = String(token?.name ?? "").trim();
    const value = String(token?.value ?? "").trim();
    if (name && value) headers[name] = value;
    return headers;
}

function cultivationApiBase(planJsonUrl) {
    const value = String(planJsonUrl ?? "").trim();
    const suffix = "/auto/plan/json";
    const index = value.indexOf(suffix);
    if (index < 0) {
        throw new Error(`计划驱动模式无法从旧计划地址推导养成 API: ${value}`);
    }
    return value.slice(0, index) + "/auto/plan/cultivation";
}

async function requestJson(method, url, body, token) {
    const response = await http.request(
        method,
        url,
        JSON.stringify(body ?? {}),
        JSON.stringify(apiHeaders(token)));
    if (response.status_code !== 200 || !response.body) {
        throw new Error(`养成计划 API 请求失败: ${method} ${url}, HTTP ${response.status_code}`);
    }
    const envelope = JSON.parse(response.body);
    if (envelope?.code !== 200) {
        throw new Error(`养成计划 API 返回失败: ${envelope?.message ?? "unknown"}`);
    }
    return envelope.data;
}

async function observeOwned(materialName, reconcileGrid) {
    if (reconcileGrid !== "Materials") {
        log.warn(`[计划驱动] 暂不支持库存页 {0}，停止并等待重新清点`, reconcileGrid);
        return null;
    }
    const param = new CountInventoryItemParam();
    param.GridScreenName = GridScreenName.CharacterDevelopmentItems;
    param.ItemName = materialName;
    const value = await dispatcher.RunCountInventoryItemTask(param);
    const observed = Number(value);
    if (!Number.isFinite(observed)) return null;
    return Math.trunc(observed);
}

async function observeOwnedBatch(materialNames) {
    const names = Array.from(materialNames ?? []).map(String).filter(Boolean);
    if (names.length === 0) return {};
    let result = {};
    try {
        result = await dispatcher.runTask(new SoloTask("CountInventoryItem", {
            gridScreenName: "Materials",
            itemNames: names,
        }));
    } catch (error) {
        log.error(`[计划驱动] 组末库存批量识别失败，将显式上报未知并停止：{0}`,
            error?.message ?? String(error));
    }
    const observedOwned = {};
    for (const name of names) {
        const count = Number(result?.[name]);
        observedOwned[name] = Number.isFinite(count) && count >= 0 ? Math.trunc(count) : -1;
    }
    return observedOwned;
}

function toPlainRewards(value) {
    const rewards = {};
    if (!value) return rewards;
    for (const name of Object.keys(value)) {
        const count = Number(value[name]);
        if (Number.isFinite(count) && count > 0) rewards[name] = Math.trunc(count);
    }
    return rewards;
}

async function reportResult(baseUrl, action, executorId, observedOwned, succeeded, reason, rewards, token) {
    const url = `${baseUrl}/execution/actions/${encodeURIComponent(action.actionId)}/result`;
    return requestJson("POST", url, {
        executorId,
        expectedRevision: action.revision,
        idempotencyKey: `${action.actionId}:result`,
        succeeded,
        observedOwned,
        rewards,
        terminationReason: reason,
    }, token);
}

export async function runCultivationInventoryReconcile(config) {
    const baseUrl = cultivationApiBase(config.bgi_tools.api.httpPullJsonConfig);
    const uid = String(config.user.uid ?? "").trim();
    if (!uid) throw new Error("库存复核模式缺少 UID");
    const executorId = `inventory-${uid}-${Date.now()}`;
    const targets = await requestJson(
        "POST", `${baseUrl}/execution/inventory-reconcile-targets?uid=${encodeURIComponent(uid)}`
            + `&executorId=${encodeURIComponent(executorId)}`,
        null, config.bgi_tools.token);
    if (targets.status === "BUSY") {
        log.warn("[计划驱动] 组末库存复核未取得租约：{0}", targets.message);
        return;
    }
    const materialNames = Array.from(targets?.materialNames ?? []);
    if (materialNames.length === 0) {
        log.info("[计划驱动] 当前没有需要组末复核的地方特产或怪物材料");
        return;
    }
    log.info("[计划驱动] 组末权威库存复核：{0}", materialNames.join("、"));
    const observedOwned = await observeOwnedBatch(materialNames);
    const response = await requestJson(
        "POST", `${baseUrl}/execution/inventory-observations?uid=${encodeURIComponent(uid)}`,
        {
            actionId: targets.actionId,
            executorId,
            expectedRevision: targets.revision,
            idempotencyKey: `${targets.actionId}:result`,
            observedOwned,
        },
        config.bgi_tools.token);
    if (response.status === "NEEDS_RECONCILE") {
        log.warn("[计划驱动] 组末库存存在未知值，已停止后续执行：{0}", response.message);
        return;
    }
    log.info("[计划驱动] 组末库存回写完成：{0} 项，{1}", response.observedCount, response.message);
}

async function executeAction(baseUrl, action, executorId, token) {
    const plan = action.plan;
    const handler = plan ? taskHandlerMap[plan.runType] : null;
    if (!handler) throw new Error(`计划器发放了不支持的行动类型: ${plan?.runType}`);

    log.info(`[计划驱动] 行动 {0}：{1}，账本还需 {2}，本批上限 {3}`,
        action.actionType, action.materialName, action.remaining, action.batchLimit);
    log.info(`[计划驱动] 选择原因：{0}`, action.message);

    let executionError = null;
    let rewards = {};
    try {
        rewards = toPlainRewards(await handler.run(plan[handler.target]));
        log.info(`[计划驱动] 实际奖励：{0}`, JSON.stringify(rewards));
    } catch (error) {
        executionError = error;
        log.error(`[计划驱动] 行动执行异常：{0}`, error?.message ?? String(error));
    }

    let observedOwned = null;
    try {
        observedOwned = await observeOwned(action.materialName, action.reconcileGrid);
        log.info(`[计划驱动] 权威库存复核：{0}={1}`, action.materialName, observedOwned);
    } catch (error) {
        log.error(`[计划驱动] 背包复核失败：{0}`, error?.message ?? String(error));
    }

    const result = await reportResult(
        baseUrl, action, executorId, observedOwned,
        executionError == null && Object.keys(rewards).length > 0,
        executionError != null
            ? `FAILED:${executionError?.message ?? executionError}`
            : Object.keys(rewards).length === 0 ? "NO_PROGRESS:NO_REWARDS" : "COMPLETED",
        rewards, token);
    log.info(`[计划驱动] 回写状态：{0}，{1}`, result.status, result.message);

    if (executionError) throw executionError;
    if (Object.keys(rewards).length === 0) {
        log.warn(`[计划驱动] 行动未产生奖励，停止本轮执行，避免重复发放同一行动`);
        return false;
    }
    return result.status === "REPLANNING";
}

export async function runPlanDrivenCultivation(config) {
    const baseUrl = cultivationApiBase(config.bgi_tools.api.httpPullJsonConfig);
    const uid = String(config.user.uid ?? "").trim();
    if (!uid) throw new Error("计划驱动模式缺少 UID");
    const executorId = `autoplan-${uid}-${Date.now()}`;
    config.run.exclude_run_exception = false;
    config.run.loop_plan = false;
    log.info(`[计划驱动] 已启用：每次只领取一个行动，权威库存回写后重新规划`);

    while (true) {
        const claimUrl = `${baseUrl}/execution/next-action?uid=${encodeURIComponent(uid)}`
            + `&executorId=${encodeURIComponent(executorId)}`;
        const action = await requestJson("POST", claimUrl, {}, config.bgi_tools.token);
        if (action.status === "NEEDS_RECONCILE") {
            log.warn(`[计划驱动] 上一行动仅执行背包复核，不再消耗树脂：{0}`, action.materialName);
            let observedOwned = null;
            try {
                observedOwned = await observeOwned(action.materialName, action.reconcileGrid);
            } catch (error) {
                log.error(`[计划驱动] 补充背包复核失败：{0}`, error?.message ?? String(error));
            }
            const result = await reportResult(
                baseUrl, action, executorId, observedOwned, false, "RECONCILE_ONLY",
                {}, config.bgi_tools.token);
            if (result.status === "REPLANNING") continue;
            log.info(`[计划驱动] 停止领取：{0}，{1}`, result.status, result.message);
            return;
        }
        if (action.status !== "ACTION") {
            log.info(`[计划驱动] 停止领取：{0}，{1}`, action.status, action.message);
            return;
        }
        const shouldContinue = await executeAction(
            baseUrl, action, executorId, config.bgi_tools.token);
        if (!shouldContinue) return;
    }
}
