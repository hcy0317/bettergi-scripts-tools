package com.cloud_guest.artifact.execution;

import com.cloud_guest.artifact.analysis.ArtifactDecision;
import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ArtifactExecutionGuard {
    public ArtifactExecutionPreflight preflight(ArtifactDecisionPlan plan, ArtifactSnapshot liveSnapshot) {
        return preflight(plan, new ArtifactExecutionObservation(
                liveSnapshot.uid(), liveSnapshot.artifactCount(), liveSnapshot.artifacts(), liveSnapshot));
    }

    public ArtifactExecutionPreflight preflight(
            ArtifactDecisionPlan plan,
            ArtifactExecutionObservation observation) {
        if (!plan.approved()) {
            return blocked(ArtifactExecutionPreflightStatus.NOT_APPROVED, "plan is not approved");
        }
        if (!plan.uid().equals(observation.uid())) {
            return blocked(ArtifactExecutionPreflightStatus.STALE_ABORT, "uid does not match approved plan");
        }
        if (plan.sourceArtifactCount() != observation.artifactCount()) {
            return blocked(ArtifactExecutionPreflightStatus.RESCAN_REQUIRED,
                    "artifact count changed; full rescan and approval are required");
        }
        if (observation.countOnly()) {
            return ready(plan, Map.of());
        }

        Map<Integer, ArtifactItem> liveByIndex = observation.artifacts().stream()
                .collect(Collectors.toMap(ArtifactItem::scanIndex, Function.identity()));
        for (ArtifactDecision decision : plan.decisions()) {
            if (decision.expectedLocked() == decision.desiredLocked()) continue;
            ArtifactItem live = liveByIndex.get(decision.scanIndex());
            if (live == null) {
                return blocked(ArtifactExecutionPreflightStatus.STALE_ABORT,
                        "target index is missing: " + decision.scanIndex());
            }
            if (!decision.expectedFingerprint().equals(live.contentFingerprint())) {
                return blocked(ArtifactExecutionPreflightStatus.STALE_ABORT,
                        "target fingerprint changed at index " + decision.scanIndex());
            }
            if (live.locked() == decision.desiredLocked()) continue;
            if (decision.expectedLocked() != live.locked()) {
                return blocked(ArtifactExecutionPreflightStatus.STALE_ABORT,
                        "target lock state changed at index " + decision.scanIndex());
            }
        }

        return ready(plan, liveByIndex);
    }

    private static ArtifactExecutionPreflight ready(
            ArtifactDecisionPlan plan,
            Map<Integer, ArtifactItem> liveByIndex) {
        List<ArtifactExecutionAction> actions = new ArrayList<>();
        for (ArtifactDecision decision : plan.decisions()) {
            ArtifactItem live = liveByIndex.get(decision.scanIndex());
            if (decision.expectedLocked() != decision.desiredLocked()
                    && (live == null || live.locked() != decision.desiredLocked())) {
                actions.add(new ArtifactExecutionAction(
                        decision.scanIndex(), decision.expectedLocked(), decision.desiredLocked(),
                        decision.expectedFingerprint()));
            }
        }
        return new ArtifactExecutionPreflight(
                ArtifactExecutionPreflightStatus.READY, actions, List.of());
    }

    private static ArtifactExecutionPreflight blocked(
            ArtifactExecutionPreflightStatus status,
            String reason) {
        return new ArtifactExecutionPreflight(status, List.of(), List.of(reason));
    }
}
