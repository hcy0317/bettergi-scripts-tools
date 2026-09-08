package com.cloud_guest.artifact.optimization;

/** A bounded, user-facing failure from the public community database boundary. */
public final class OptimizationCommunityException extends IllegalStateException {
    private final int status;

    public OptimizationCommunityException(int status, String message) {
        super(message);
        this.status = status;
    }

    public OptimizationCommunityException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() { return status; }
}
