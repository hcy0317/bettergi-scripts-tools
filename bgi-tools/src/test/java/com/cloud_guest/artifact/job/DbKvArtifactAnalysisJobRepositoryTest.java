package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbKvArtifactAnalysisJobRepositoryTest {

    @Test
    void findsOneJobByKeySuffixWithoutDeserializingTheEntireHistory() {
        ArtifactJsonStore store = mock(ArtifactJsonStore.class);
        ArtifactAnalysisJob job = new ArtifactAnalysisJob(
                "job-1", "102550550", ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER,
                ArtifactAnalysisJobStatus.HOST_CLAIMED,
                null, null, null, "2026-08-28T00:00:00Z", "2026-08-28T00:00:01Z", null);
        when(store.getByKeySuffix(
                "artifact-analysis-job", ":job-1", ArtifactAnalysisJob.class))
                .thenReturn(Optional.of(job));

        var found = new DbKvArtifactAnalysisJobRepository(store).findById("job-1");

        assertThat(found).contains(job);
        verify(store).getByKeySuffix(
                "artifact-analysis-job", ":job-1", ArtifactAnalysisJob.class);
        verify(store, never()).list("artifact-analysis-job", ArtifactAnalysisJob.class);
    }

    @Test
    void listPageReadsOnlyBoundedLightweightSummaries() {
        ArtifactJsonStore store = mock(ArtifactJsonStore.class);
        ArtifactAnalysisJobSummary summary = new ArtifactAnalysisJobSummary(
                "job-1", "102550550", ArtifactLaunchOperation.ANALYZE,
                ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                null, null, null,
                "2026-08-28T00:00:00Z", "2026-08-28T00:00:01Z", null);
        when(store.listByKeyPrefixLimited(
                "artifact-analysis-job-summary", "102550550:",
                ArtifactAnalysisJobSummary.class, 100))
                .thenReturn(List.of(summary));
        when(store.get(
                "artifact-analysis-job-summary-migration", "102550550", Boolean.class))
                .thenReturn(Optional.of(true));

        var result = new DbKvArtifactAnalysisJobRepository(store)
                .findSummariesByUid("102550550", 100);

        assertThat(result).containsExactly(summary);
        verify(store, never()).listByKeyPrefixLimited(
                "artifact-analysis-job", "102550550:",
                ArtifactAnalysisJob.class, 100);
        verify(store, never()).listByKeyPrefix(
                "artifact-analysis-job", "102550550:",
                ArtifactAnalysisJob.class);
    }

    @Test
    void summaryMigrationContinuesFromStoredCursorBeforeMarkingComplete() {
        ArtifactJsonStore store = mock(ArtifactJsonStore.class);
        ArtifactAnalysisJob legacy = new ArtifactAnalysisJob(
                "legacy-101", "102550550", ArtifactLaunchOperation.ANALYZE,
                ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                null, null, null,
                "2026-08-27T00:00:00Z", "2026-08-27T00:00:01Z", null);
        ArtifactAnalysisJobSummary summary = ArtifactAnalysisJobSummary.from(legacy);
        when(store.get(
                "artifact-analysis-job-summary-migration", "102550550", Boolean.class))
                .thenReturn(Optional.of(false));
        when(store.get(
                "artifact-analysis-job-summary-migration-cursor", "102550550", Long.class))
                .thenReturn(Optional.of(500L));
        when(store.listByKeyPrefixBeforeId(
                "artifact-analysis-job", "102550550:",
                ArtifactAnalysisJob.class, 100, 500L))
                .thenReturn(List.of(
                        new ArtifactJsonStore.StoredValue<>(499L, legacy)));
        when(store.put(
                "artifact-analysis-job-summary", "102550550:legacy-101", summary))
                .thenReturn(summary);
        when(store.listByKeyPrefixLimited(
                "artifact-analysis-job-summary", "102550550:",
                ArtifactAnalysisJobSummary.class, 1000))
                .thenReturn(List.of(summary));

        var result = new DbKvArtifactAnalysisJobRepository(store)
                .findSummariesByUid("102550550", 1000);

        assertThat(result).containsExactly(summary);
        verify(store).put(
                "artifact-analysis-job-summary-migration-cursor", "102550550", 499L);
        verify(store).put(
                "artifact-analysis-job-summary-migration", "102550550", true);
    }
}
