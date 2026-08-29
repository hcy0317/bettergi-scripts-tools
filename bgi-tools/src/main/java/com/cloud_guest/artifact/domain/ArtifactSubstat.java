package com.cloud_guest.artifact.domain;

public record ArtifactSubstat(String key, double value, boolean dormant) {
    public ArtifactSubstat {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("substat key is required");
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("substat value must be finite and nonnegative");
    }

    public ArtifactSubstat(String key, double value) {
        this(key, value, false);
    }
}
