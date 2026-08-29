package com.cloud_guest.artifact.execution;

import com.cloud_guest.artifact.analysis.ArtifactDecision;
import com.cloud_guest.artifact.analysis.ArtifactDecisionKind;
import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.domain.ArtifactSubstat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactExecutionGuardTest {

    private final ArtifactExecutionGuard guard = new ArtifactExecutionGuard();

    @Test
    void changedArtifactCountRequiresFullRescanBeforeAnyClick() {
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactDecisionPlan plan = plan(source, decision(source.artifacts().getFirst(), true));
        ArtifactSnapshot live = snapshot(List.of(item(0, false), item(1, false)));

        ArtifactExecutionPreflight result = guard.preflight(plan, live);

        assertThat(result.status()).isEqualTo(ArtifactExecutionPreflightStatus.RESCAN_REQUIRED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void sameCountButDifferentTargetFingerprintAbortsWholeBatch() {
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactDecisionPlan plan = plan(source, decision(source.artifacts().getFirst(), true));
        ArtifactItem changed = new ArtifactItem(
                0, "GoldenTroupe", "circlet", 4, 5, "critRate_",
                List.of(new ArtifactSubstat("critDMG_", 14.0)), "Furina", false);

        ArtifactExecutionPreflight result = guard.preflight(plan, snapshot(List.of(changed)));

        assertThat(result.status()).isEqualTo(ArtifactExecutionPreflightStatus.STALE_ABORT);
        assertThat(result.actions()).isEmpty();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("fingerprint"));
    }

    @Test
    void matchingCountFingerprintAndLockStateProducesOnlyRequiredActions() {
        ArtifactSnapshot source = snapshot(List.of(item(0, false), item(1, true)));
        ArtifactDecisionPlan plan = plan(
                source,
                decision(source.artifacts().get(0), true),
                decision(source.artifacts().get(1), true));

        ArtifactExecutionPreflight result = guard.preflight(plan, source);

        assertThat(result.status()).isEqualTo(ArtifactExecutionPreflightStatus.READY);
        assertThat(result.actions()).containsExactly(
                new ArtifactExecutionAction(0, false, true, source.artifacts().get(0).contentFingerprint()));
    }

    @Test
    void unchangedCountCanSkipTargetPreflightAndProduceApprovedActions() {
        ArtifactSnapshot source = snapshot(List.of(item(0, false), item(1, true)));
        ArtifactDecisionPlan plan = plan(
                source,
                decision(source.artifacts().get(0), true),
                decision(source.artifacts().get(1), true));
        ArtifactExecutionObservation countOnly = new ArtifactExecutionObservation(
                source.uid(), source.artifactCount(), List.of(), null, true);

        ArtifactExecutionPreflight result = guard.preflight(plan, countOnly);

        assertThat(result.status()).isEqualTo(ArtifactExecutionPreflightStatus.READY);
        assertThat(result.actions()).containsExactly(
                new ArtifactExecutionAction(0, false, true, source.artifacts().get(0).contentFingerprint()));
    }

    @Test
    void repeatedExecutionSkipsTargetThatAlreadyHasItsDesiredLockState() {
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactDecisionPlan plan = plan(source, decision(source.artifacts().getFirst(), true));
        ArtifactItem alreadyLocked = item(0, true);

        ArtifactExecutionPreflight result = guard.preflight(
                plan, snapshot(List.of(alreadyLocked)));

        assertThat(result.status()).isEqualTo(ArtifactExecutionPreflightStatus.READY);
        assertThat(result.actions()).isEmpty();
    }

    private static ArtifactDecisionPlan plan(ArtifactSnapshot source, ArtifactDecision... decisions) {
        return new ArtifactDecisionPlan(
                "plan-1", source.uid(), source.artifactCount(), source.snapshotDigest(),
                true, List.of(decisions));
    }

    private static ArtifactDecision decision(ArtifactItem item, boolean desiredLocked) {
        return new ArtifactDecision(
                item.scanIndex(), item.contentFingerprint(), item.locked(), desiredLocked,
                "build", 90, 90, true, "SET_MATCH", ArtifactDecisionKind.KEEP, List.of());
    }

    private static ArtifactSnapshot snapshot(List<ArtifactItem> artifacts) {
        return ArtifactSnapshot.create(
                "102550550", "scan", "OBTAINED_AT_DESC", "genshin-7.0", artifacts);
    }

    private static ArtifactItem item(int index, boolean locked) {
        return new ArtifactItem(
                index, "GoldenTroupe", "circlet", 20, 5, "critRate_",
                List.of(new ArtifactSubstat("critDMG_", 21.0)), "Furina", locked);
    }
}
