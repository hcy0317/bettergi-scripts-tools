package com.cloud_guest.artifact.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ArtifactSnapshot(
        String uid,
        String scanSessionId,
        int artifactCount,
        String orderingMode,
        String catalogVersion,
        List<ArtifactItem> artifacts,
        String snapshotDigest) {

    public ArtifactSnapshot {
        if (uid == null || !uid.matches("[0-9]{6,12}")) throw new IllegalArgumentException("valid uid is required");
        if (scanSessionId == null || scanSessionId.isBlank()) throw new IllegalArgumentException("scan session id is required");
        if (orderingMode == null || orderingMode.isBlank()) throw new IllegalArgumentException("ordering mode is required");
        if (catalogVersion == null || catalogVersion.isBlank()) throw new IllegalArgumentException("catalog version is required");
        artifacts = List.copyOf(artifacts);
        if (artifactCount < artifacts.size()) throw new IllegalArgumentException("artifact count cannot be smaller than items");
        Set<Integer> indices = new HashSet<>();
        if (artifacts.stream().anyMatch(item -> !indices.add(item.scanIndex()))) {
            throw new IllegalArgumentException("scan indices must be unique");
        }
        if (snapshotDigest == null || snapshotDigest.isBlank()) throw new IllegalArgumentException("snapshot digest is required");
    }

    public static ArtifactSnapshot create(
            String uid,
            String scanSessionId,
            String orderingMode,
            String catalogVersion,
            List<ArtifactItem> artifacts) {
        List<ArtifactItem> immutable = List.copyOf(artifacts);
        String itemDigest = immutable.stream()
                .map(item -> item.scanIndex() + ":" + item.contentFingerprint() + ":" + item.locked())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String digest = ArtifactHashes.sha256(String.join("|",
                uid, scanSessionId, Integer.toString(immutable.size()), orderingMode, catalogVersion, itemDigest));
        return new ArtifactSnapshot(
                uid, scanSessionId, immutable.size(), orderingMode, catalogVersion, immutable, digest);
    }
}
