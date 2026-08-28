import test from "node:test";
import assert from "node:assert/strict";

import {
  artifactDecisionEvaluation,
  artifactDecisionRows,
  artifactDecisionScores,
  artifactExecutionSummary,
  artifactExecutionTargets,
  artifactHasDormantSubstat,
  artifactLockPlanFilterOptions,
  filterAndSortArtifactDecisionRows,
  preferredArtifactJobId,
} from "../src/features/artifact-analysis/lockPlanModel.js";

const job = {
  snapshot: {
    artifacts: [
      {scanIndex: 0, setKey: "GoldenTroupe", slotKey: "flower", level: 20},
      {scanIndex: 1, setKey: "MarechausseeHunter", slotKey: "circlet", level: 0},
      {scanIndex: 2, setKey: "GoldenTroupe", slotKey: "circlet", level: 12},
    ],
  },
  analysisResult: {
    buildIds: ["furina"],
    decisions: [
      {scanIndex: 0, kind: "KEEP", expectedLocked: false, desiredLocked: true, currentScore: 91, potentialScore: 91, preferredMain: true, bestBuildId: "furina", buildCurrentScores: [91], buildPotentialScores: [91], buildPreferredMains: [true], buildSetMatches: [true]},
      {scanIndex: 1, kind: "REJECT", expectedLocked: true, desiredLocked: false, currentScore: 40, potentialScore: 62, preferredMain: false, bestBuildId: "neuvillette"},
      {scanIndex: 2, kind: "KEEP", expectedLocked: true, desiredLocked: true, currentScore: 58, potentialScore: 83, preferredMain: true, bestBuildId: "furina"},
    ],
  },
};

test("lock plan rows join artifacts and expose stable filter options", () => {
  const rows = artifactDecisionRows(job);
  assert.equal(rows[0].artifact.setKey, "GoldenTroupe");
  assert.deepEqual(artifactLockPlanFilterOptions(rows), {
    setKeys: ["GoldenTroupe", "MarechausseeHunter"],
    slotKeys: ["circlet", "flower"],
    levels: [0, 12, 20],
  });
});

test("lock plan filters recommended artifacts and sorts by potential", () => {
  const rows = filterAndSortArtifactDecisionRows(artifactDecisionRows(job), {
    view: "recommended",
    setKey: "GoldenTroupe",
    slotKey: "all",
    levelRange: [13, 20],
    sort: "potential-desc",
  });
  assert.deepEqual(rows.map(row => row.scanIndex), [0]);
  assert.deepEqual(artifactExecutionTargets(rows).map(row => row.scanIndex), [0]);
  assert.deepEqual(artifactExecutionSummary(rows), {lock: 1, unlock: 0, total: 1});
});

test("recommended and other filters produce different execution targets", () => {
  const allRows = artifactDecisionRows(job);
  const recommended = filterAndSortArtifactDecisionRows(allRows, {view: "recommended", levelRange: [0, 20]});
  const other = filterAndSortArtifactDecisionRows(allRows, {view: "other", levelRange: [0, 20]});

  assert.deepEqual(artifactExecutionSummary(recommended), {lock: 1, unlock: 0, total: 1});
  assert.deepEqual(artifactExecutionSummary(other), {lock: 0, unlock: 1, total: 1});
});

test("all filter keeps every decision and combines lock and unlock targets", () => {
  const rows = filterAndSortArtifactDecisionRows(artifactDecisionRows(job), {
    view: "all",
    levelRange: [0, 20],
  });

  assert.deepEqual(rows.map(row => row.scanIndex), [0, 2, 1]);
  assert.deepEqual(artifactExecutionSummary(rows), {lock: 1, unlock: 1, total: 2});
});

test("evaluation and independent build scores remain useful for old records", () => {
  const rows = artifactDecisionRows(job);
  assert.deepEqual(artifactDecisionEvaluation(rows[0]), {label: "极品", type: "success"});
  assert.deepEqual(artifactDecisionEvaluation(rows[1]), {label: "主属性不匹配", type: "danger"});
  assert.deepEqual(artifactDecisionScores({...rows[2], buildIds: []}), [{
    buildId: "furina",
    currentScore: 58,
    potentialScore: 83,
    preferredMain: true,
    setFit: "",
  }]);
});

test("dormant fourth substat remains visible in the lock-plan view model", () => {
  assert.equal(artifactHasDormantSubstat({artifact: {substats: [
    {key: "critRate_", value: 3.1, dormant: false},
    {key: "enerRech_", value: 4.5, dormant: true},
  ]}}), true);
  assert.equal(artifactHasDormantSubstat({artifact: {substats: [
    {key: "critRate_", value: 3.1},
  ]}}), false);
});

test("build score rail keeps every threshold match and sorts by the active score", () => {
  const finished = {
    artifact: {level: 20},
    buildIds: ["ninety", "eighty-five", "below", "wrong-main"],
    buildCurrentScores: [90, 85, 79, 95],
    buildPotentialScores: [90, 85, 79, 95],
    buildPreferredMains: [true, true, true, false],
    buildSetMatches: [true, false, true, true],
  };
  assert.deepEqual(
    artifactDecisionScores(finished, {finishedScoreThreshold: 80}).map(score => score.buildId),
    ["ninety", "eighty-five"]
  );

  const unfinished = {
    ...finished,
    artifact: {level: 0},
    buildPotentialScores: [76, 88, 74, 99],
  };
  assert.deepEqual(
    artifactDecisionScores(unfinished, {unfinishedPotentialThreshold: 75}).map(score => score.buildId),
    ["eighty-five", "ninety"]
  );
});

test("build score rail never falls back to zero while algorithm settings are unavailable", () => {
  const finished = {
    artifact: {level: 20},
    buildIds: ["at-threshold", "below-threshold"],
    buildCurrentScores: [80, 79],
    buildPotentialScores: [95, 95],
    buildPreferredMains: [true, true],
    buildSetMatches: [true, true],
  };
  assert.deepEqual(
    artifactDecisionScores(finished).map(score => score.buildId),
    ["at-threshold"]
  );
  assert.deepEqual(
    artifactDecisionScores(finished, {finishedScoreThreshold: null}).map(score => score.buildId),
    ["at-threshold"]
  );

  const unfinished = {
    ...finished,
    artifact: {level: 19},
    buildCurrentScores: [99, 99],
    buildPotentialScores: [75, 74],
  };
  assert.deepEqual(
    artifactDecisionScores(unfinished).map(score => score.buildId),
    ["at-threshold"]
  );
  assert.deepEqual(
    artifactDecisionScores(unfinished, {unfinishedPotentialThreshold: undefined}).map(score => score.buildId),
    ["at-threshold"]
  );
});

test("newly arrived analyzable jobs replace the previously newest selection", () => {
  const jobs = [{id: "new", analysisResult: {}}, {id: "old", analysisResult: {}}];
  assert.equal(preferredArtifactJobId(jobs, "old", "old"), "new");
  assert.equal(preferredArtifactJobId(jobs, "old", "older-manual-selection"), "old");
  assert.equal(preferredArtifactJobId(jobs, "missing", "old"), "new");
});
