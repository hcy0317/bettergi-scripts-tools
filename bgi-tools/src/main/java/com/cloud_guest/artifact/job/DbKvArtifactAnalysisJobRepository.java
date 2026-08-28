package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbKvArtifactAnalysisJobRepository implements ArtifactAnalysisJobRepository {
    private static final String TYPE = "artifact-analysis-job";
    private final ArtifactJsonStore store;

    public DbKvArtifactAnalysisJobRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public ArtifactAnalysisJob save(ArtifactAnalysisJob job) {
        return store.put(TYPE, key(job.uid(), job.id()), job);
    }

    @Override
    public Optional<ArtifactAnalysisJob> findById(String id) {
        return store.getByKeySuffix(TYPE, ":" + id, ArtifactAnalysisJob.class);
    }

    @Override
    public List<ArtifactAnalysisJob> findByUid(String uid) {
        return store.listByKeyPrefix(TYPE, uid + ":", ArtifactAnalysisJob.class);
    }

    @Override
    public boolean delete(String uid, String id) {
        return store.delete(TYPE, key(uid, id));
    }

    private static String key(String uid, String id) {
        return uid + ":" + id;
    }
}
