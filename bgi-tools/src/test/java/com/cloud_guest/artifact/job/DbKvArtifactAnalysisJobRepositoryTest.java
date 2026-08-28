package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
}
