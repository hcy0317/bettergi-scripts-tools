import test from "node:test";
import assert from "node:assert/strict";

import {
  artifactCountReconciliation,
  canApproveArtifactJob,
  canDeleteArtifactJob,
  canExecuteArtifactJob,
  artifactHostHasAcceptedJob,
  artifactJobStatusMeta,
  artifactNonFiveStarCountStorageKey,
  artifactOperationMeta,
  waitForArtifactHostClaim,
  waitForArtifactJobCompletion,
  shouldRefreshArtifactJobs,
  hasActiveArtifactJobs,
  artifactCharacterScanStatusMeta,
} from "../src/features/artifact-analysis/model.js";

test("manual non-five-star count reconciles analyzable items with total inventory", () => {
  const job = {
    id: "job-1",
    snapshot: {
      artifactCount: 1125,
      artifacts: Array.from({length: 1121}, (_, scanIndex) => ({scanIndex})),
    },
  };

  assert.deepEqual(artifactCountReconciliation(job, 4), {
    matches: true,
    totalCount: 1125,
    analyzableCount: 1121,
    nonFiveStarCount: 4,
    combinedCount: 1125,
  });
  assert.deepEqual(artifactCountReconciliation(job, 3), {
    matches: false,
    totalCount: 1125,
    analyzableCount: 1121,
    nonFiveStarCount: 3,
    combinedCount: 1124,
  });
  assert.equal(artifactCountReconciliation(job, null), null);
  assert.equal(artifactCountReconciliation(job, -1), null);
  assert.deepEqual(artifactCountReconciliation({
    snapshot: {artifactCount: 1125, analyzableArtifactCount: 1121},
  }, 4), {
    matches: true,
    totalCount: 1125,
    analyzableCount: 1121,
    nonFiveStarCount: 4,
    combinedCount: 1125,
  });
  assert.equal(artifactNonFiveStarCountStorageKey(" 102550550 "), "artifact-analysis:non-five-star:102550550");
});

test("silent job polling pauses while hidden and never overlaps", () => {
  assert.equal(shouldRefreshArtifactJobs({silent: true, documentHidden: true}), false);
  assert.equal(shouldRefreshArtifactJobs({silent: true, refreshing: true}), false);
  assert.equal(shouldRefreshArtifactJobs({silent: false, documentHidden: true}), true);
  assert.equal(shouldRefreshArtifactJobs({silent: true}), true);
});

test("job polling runs only while a host operation is active", () => {
  assert.equal(hasActiveArtifactJobs([{status: "FAILED"}, {status: "COMPLETED"}]), false);
  assert.equal(hasActiveArtifactJobs([{status: "READY_FOR_REVIEW"}]), false);
  assert.equal(hasActiveArtifactJobs([{status: "WAITING_FOR_HOST"}]), true);
  assert.equal(hasActiveArtifactJobs([{status: "HOST_CLAIMED"}]), true);
});

test("character scan status stays visible without holding the action spinner", () => {
  assert.deepEqual(artifactCharacterScanStatusMeta({status: "HOST_CLAIMED"}), {
    title: "BetterGI 正在检测游戏角色",
    type: "info",
  });
  assert.deepEqual(artifactCharacterScanStatusMeta({
    status: "FAILED",
    errorMessage: "Object reference not set to an instance of an object.",
  }), {
    title: "角色检测失败：BetterGI 角色检测发生内部错误，请重试或查看日志。",
    type: "error",
  });
});

test("artifact job actions follow the review and approval state machine", () => {
  assert.equal(canApproveArtifactJob({ status: "READY_FOR_REVIEW" }), true);
  assert.equal(canApproveArtifactJob({ status: "APPROVED" }), false);
  assert.equal(canExecuteArtifactJob({ status: "APPROVED", decisionPlan: { approved: true } }), true);
  assert.equal(canExecuteArtifactJob({ status: "COMPLETED", decisionPlan: { approved: true } }), true);
  assert.equal(canExecuteArtifactJob({ status: "FAILED", decisionPlan: { approved: true } }), true);
  assert.equal(canExecuteArtifactJob({ status: "STALE_ABORT", decisionPlan: { approved: true } }), true);
  assert.equal(canExecuteArtifactJob({ status: "WAITING_FOR_HOST", decisionPlan: { approved: true } }), false);
  assert.equal(canExecuteArtifactJob({ status: "HOST_CLAIMED", decisionPlan: { approved: true } }), false);
  assert.equal(canExecuteArtifactJob({ status: "READY_TO_EXECUTE", decisionPlan: { approved: true } }), false);
  assert.equal(canExecuteArtifactJob({ status: "RESCAN_REQUIRED", decisionPlan: { approved: false } }), false);
  assert.equal(canDeleteArtifactJob({ status: "WAITING_FOR_HOST" }), true);
  assert.equal(canDeleteArtifactJob({ status: "COMPLETED" }), true);
  assert.equal(canDeleteArtifactJob({ status: "HOST_CLAIMED" }), false);
  assert.equal(canDeleteArtifactJob({ status: "READY_TO_EXECUTE" }), false);
});

test("artifact launch waits briefly for an already-running BetterGI host", async () => {
  const states = [
    {id: "job-1", status: "WAITING_FOR_HOST"},
    {id: "job-1", status: "HOST_CLAIMED"},
  ];
  let calls = 0;
  const job = await waitForArtifactHostClaim(
    "job-1",
    async () => states[Math.min(calls++, states.length - 1)],
    {attempts: 3, delay: 0, sleep: async () => {}}
  );

  assert.equal(job.status, "HOST_CLAIMED");
  assert.equal(calls, 2);
});

test("artifact launch does not offer a second start after the host already finished", async () => {
  let calls = 0;
  const job = await waitForArtifactHostClaim(
    "job-1",
    async () => {
      calls++;
      return {id: "job-1", status: "FAILED"};
    },
    {attempts: 3, delay: 0, sleep: async () => {}}
  );

  assert.equal(calls, 1);
  assert.equal(artifactHostHasAcceptedJob(job), true);
});

test("artifact launch prompt does not wait for a host that has not accepted the request", async () => {
  const states = Array.from({length: 29}, () => ({id: "job-1", status: "WAITING_FOR_HOST"}));
  states.push({id: "job-1", status: "HOST_CLAIMED"});
  let calls = 0;

  const job = await waitForArtifactHostClaim(
    "job-1",
    async () => states[Math.min(calls++, states.length - 1)],
    {delay: 0, sleep: async () => {}}
  );

  assert.equal(job.status, "WAITING_FOR_HOST");
  assert.equal(calls, 1);
});

test("artifact states and operations have concise user-facing metadata", () => {
  assert.deepEqual(artifactJobStatusMeta("RESCAN_REQUIRED"), {
    label: "需要重新扫描",
    type: "warning",
  });
  assert.deepEqual(artifactOperationMeta("REBUILD_NATIVE_PLANS"), {
    label: "重建原神方案",
    launchHost: "native-sync",
  });
  assert.deepEqual(artifactOperationMeta("SCAN_CHARACTER_ROSTER"), {
    label: "检测角色并更新配装",
    launchHost: "characters",
  });
  assert.deepEqual(artifactJobStatusMeta("FAILED", "用户已停止任务"), {
    label: "已停止",
    type: "info",
  });
  assert.equal(artifactJobStatusMeta("UNKNOWN").label, "状态未知");
});

test("character scan waits for the host to finish applying build activation", async () => {
  const states = [
    {id: "job-roster", status: "HOST_CLAIMED"},
    {id: "job-roster", status: "COMPLETED"},
  ];
  let calls = 0;
  const observed = [];

  const job = await waitForArtifactJobCompletion(
    "job-roster",
    async () => states[Math.min(calls++, states.length - 1)],
    {attempts: 3, delay: 0, sleep: async () => {}, onUpdate: job => observed.push(job.status)},
  );

  assert.equal(job.status, "COMPLETED");
  assert.equal(calls, 2);
  assert.deepEqual(observed, ["HOST_CLAIMED", "COMPLETED"]);
});

test("unbounded completion observation continues past the finite polling budget", async () => {
  const states = [
    ...Array.from({length: 305}, () => ({id: "job-lock", status: "HOST_CLAIMED"})),
    {id: "job-lock", status: "COMPLETED"},
  ];
  let calls = 0;

  const job = await waitForArtifactJobCompletion(
    "job-lock",
    async () => states[Math.min(calls++, states.length - 1)],
    {attempts: null, delay: 0, sleep: async () => {}},
  );

  assert.equal(job.status, "COMPLETED");
  assert.equal(calls, 306);
});

test("artifact completion observation retries a transient request failure", async () => {
  let calls = 0;
  const errors = [];

  const job = await waitForArtifactJobCompletion(
    "job-retry",
    async () => {
      calls++;
      if (calls === 1) throw new Error("temporary network failure");
      return {id: "job-retry", status: "COMPLETED"};
    },
    {
      attempts: 3,
      delay: 0,
      sleep: async () => {},
      onError: (_error, count) => errors.push(count),
    },
  );

  assert.equal(job.status, "COMPLETED");
  assert.equal(calls, 2);
  assert.deepEqual(errors, [1]);
});

test("artifact completion observation stops after its consecutive error budget", async () => {
  let calls = 0;

  await assert.rejects(
    waitForArtifactJobCompletion(
      "job-failing",
      async () => {
        calls++;
        throw new Error("service unavailable");
      },
      {attempts: null, delay: 0, sleep: async () => {}, maxConsecutiveErrors: 2},
    ),
    /service unavailable/,
  );
  assert.equal(calls, 2);
});
