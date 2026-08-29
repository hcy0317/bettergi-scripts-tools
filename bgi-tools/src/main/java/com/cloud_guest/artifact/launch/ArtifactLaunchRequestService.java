package com.cloud_guest.artifact.launch;

import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.List;
import java.util.function.Supplier;

public class ArtifactLaunchRequestService {
    private static final int VERSION = 1;
    private static final String KIND = "artifact-analysis";
    private static final String TOKEN_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final Supplier<Path> betterGiRoot;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;

    public ArtifactLaunchRequestService(Path betterGiRoot, ObjectMapper objectMapper, Clock clock, Duration ttl) {
        this(() -> betterGiRoot, objectMapper, clock, ttl);
    }

    public ArtifactLaunchRequestService(
            Supplier<Path> betterGiRoot,
            ObjectMapper objectMapper,
            Clock clock,
            Duration ttl) {
        this.betterGiRoot = betterGiRoot;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    public ArtifactLaunchResult create(String uid, String jobId, ArtifactLaunchOperation operation) {
        return create(uid, jobId, operation, null, List.of(), null, null);
    }

    public ArtifactLaunchResult create(
            String uid,
            String jobId,
            ArtifactLaunchOperation operation,
            Integer sourceArtifactCount,
            List<ArtifactLaunchTarget> targets,
            Integer nativeCapacity,
            String nativePlanDigest) {
        if (uid == null || !uid.matches("[0-9]{6,12}")) throw new IllegalArgumentException("valid uid is required");
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("job id is required");
        if (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && (sourceArtifactCount == null || sourceArtifactCount < 0)) {
            throw new IllegalArgumentException("lock execution requires its approved artifact count");
        }
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(ttl);
        String token = UUID.randomUUID().toString();
        ArtifactLaunchRequest request = new ArtifactLaunchRequest(
                VERSION, KIND, uid, jobId, operation, createdAt.toString(), expiresAt.toString(),
                sourceArtifactCount, targets, nativeCapacity, nativePlanDigest,
                null, null, null, null, null);
        Path requestRoot = requestRoot();
        try {
            Files.createDirectories(requestRoot);
            writeAtomically(requestRoot.resolve(token + ".json"), request);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to create BetterGI artifact launch request", exception);
        }
        return new ArtifactLaunchResult(
                token,
                "BetterGIArtifact://" + operation.uriHost() + "?request=" + token,
                expiresAt.toString(),
                "分析任务已创建，正在交给 BetterGI 宿主");
    }

    public ArtifactLaunchResult createCharacterRoster(
            String uid,
            String jobId,
            ArtifactBuildAutoActivationSettings settings,
            String gameNickname,
            String miliastraNickname,
            String miliastraCharacterKey) {
        if (settings == null) throw new IllegalArgumentException("auto activation settings are required");
        if (uid == null || !uid.matches("[0-9]{6,12}")) throw new IllegalArgumentException("valid uid is required");
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("job id is required");
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(ttl);
        String token = UUID.randomUUID().toString();
        ArtifactLaunchRequest request = new ArtifactLaunchRequest(
                VERSION, KIND, uid, jobId, ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER,
                createdAt.toString(), expiresAt.toString(), null, List.of(), null, null,
                settings.levelThreshold(), settings.favoriteOverride(),
                gameNickname == null ? null : gameNickname.trim(),
                miliastraNickname == null ? null : miliastraNickname.trim(),
                "MannequinBoy".equals(miliastraCharacterKey)
                        ? "MannequinBoy" : "MannequinGirl");
        Path requestRoot = requestRoot();
        try {
            Files.createDirectories(requestRoot);
            writeAtomically(requestRoot.resolve(token + ".json"), request);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to create BetterGI character roster request", exception);
        }
        return new ArtifactLaunchResult(
                token,
                "BetterGIArtifact://characters?request=" + token,
                expiresAt.toString(),
                "角色检测任务已创建，正在交给 BetterGI 宿主");
    }

    public ArtifactLaunchRequest consume(String token) {
        return consume(token, null, null, null);
    }

    public boolean isExpired(String createdAtUtc, Instant now) {
        try {
            return !Instant.parse(createdAtUtc).plus(ttl).isAfter(now);
        } catch (DateTimeParseException exception) {
            return true;
        }
    }

    public ArtifactLaunchRequest consume(
            String token,
            String expectedUid,
            String expectedJobId,
            ArtifactLaunchOperation expectedOperation) {
        if (token == null || !token.matches(TOKEN_PATTERN)) {
            throw new IllegalArgumentException("invalid launch request token");
        }
        Path requestRoot = requestRoot();
        Path requestFile = requestRoot.resolve(token + ".json");
        Path consumedFile = requestRoot.resolve("consumed").resolve(token + ".json");
        if (!Files.isRegularFile(requestFile)) {
            if (Files.isRegularFile(consumedFile)) {
                try {
                    return readAndValidate(
                            consumedFile, expectedUid, expectedJobId, expectedOperation, false);
                } catch (IOException exception) {
                    throw new IllegalStateException("unable to authorize consumed launch request", exception);
                }
            }
            throw new IllegalStateException("launch request was not found");
        }
        try {
            ArtifactLaunchRequest request = readAndValidate(
                    requestFile, expectedUid, expectedJobId, expectedOperation, true);
            Files.createDirectories(consumedFile.getParent());
            moveAtomically(requestFile, consumedFile);
            return request;
        } catch (IOException exception) {
            throw new IllegalStateException("unable to consume launch request", exception);
        }
    }

    public ArtifactLaunchRequest authorizeClaimed(
            String token,
            String expectedUid,
            String expectedJobId,
            ArtifactLaunchOperation expectedOperation) {
        if (token == null || !token.matches(TOKEN_PATTERN)) {
            throw new IllegalArgumentException("invalid launch request token");
        }
        Path claimedFile = requestRoot().resolve("consumed").resolve(token + ".json");
        if (!Files.isRegularFile(claimedFile)) {
            throw new IllegalStateException("launch request has not been claimed");
        }
        try {
            return readAndValidate(
                    claimedFile, expectedUid, expectedJobId, expectedOperation, false);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to authorize claimed launch request", exception);
        }
    }

    public ArtifactLaunchRequest complete(
            String token,
            String expectedUid,
            String expectedJobId,
            ArtifactLaunchOperation expectedOperation) {
        if (token == null || !token.matches(TOKEN_PATTERN)) {
            throw new IllegalArgumentException("invalid launch request token");
        }
        Path root = requestRoot();
        Path activeFile = root.resolve(token + ".json");
        Path consumedFile = root.resolve("consumed").resolve(token + ".json");
        Path completedFile = root.resolve("completed").resolve(token + ".json");
        if (Files.isRegularFile(completedFile)) {
            try {
                return readAndValidate(
                        completedFile, expectedUid, expectedJobId, expectedOperation, false);
            } catch (IOException exception) {
                throw new IllegalStateException("unable to authorize completed launch request", exception);
            }
        }
        boolean claimed = Files.isRegularFile(consumedFile);
        Path source = claimed ? consumedFile : activeFile;
        if (!Files.isRegularFile(source)) throw new IllegalStateException("launch request was not found");
        try {
            ArtifactLaunchRequest request = readAndValidate(
                    source, expectedUid, expectedJobId, expectedOperation, !claimed);
            Files.createDirectories(completedFile.getParent());
            moveAtomically(source, completedFile);
            return request;
        } catch (IOException exception) {
            throw new IllegalStateException("unable to complete launch request", exception);
        }
    }

    public synchronized int revokeForJob(String expectedUid, String expectedJobId) {
        Path root = requestRoot();
        try {
            if (containsMatchingRequest(root.resolve("consumed"), expectedUid, expectedJobId)) {
                throw new IllegalStateException("任务已被 BetterGI 接收，当前不能删除");
            }
            int revoked = deleteMatchingRequests(root, expectedUid, expectedJobId);
            revoked += deleteMatchingRequests(root.resolve("completed"), expectedUid, expectedJobId);
            if (containsMatchingRequest(root.resolve("consumed"), expectedUid, expectedJobId)) {
                throw new IllegalStateException("任务已被 BetterGI 接收，当前不能删除");
            }
            return revoked;
        } catch (IOException exception) {
            throw new IllegalStateException("无法撤销 BetterGI 启动请求", exception);
        }
    }

    public synchronized boolean hasAcceptedRequest(String expectedUid, String expectedJobId) {
        try {
            Path root = requestRoot();
            return containsMatchingRequest(
                    root.resolve("consumed"), expectedUid, expectedJobId)
                    || containsMatchingRequest(
                    root.resolve("completed"), expectedUid, expectedJobId);
        } catch (IOException exception) {
            throw new IllegalStateException("无法检查 BetterGI 已接受启动请求", exception);
        }
    }

    private int deleteMatchingRequests(Path directory, String expectedUid, String expectedJobId)
            throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        int deleted = 0;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (matchesRequest(path, expectedUid, expectedJobId) && Files.deleteIfExists(path)) deleted++;
            }
        }
        return deleted;
    }

    private boolean containsMatchingRequest(Path directory, String expectedUid, String expectedJobId)
            throws IOException {
        if (!Files.isDirectory(directory)) return false;
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .anyMatch(path -> matchesRequest(path, expectedUid, expectedJobId));
        }
    }

    private boolean matchesRequest(Path path, String expectedUid, String expectedJobId) {
        try {
            ArtifactLaunchRequest request = objectMapper.readValue(path.toFile(), ArtifactLaunchRequest.class);
            return request.version() == VERSION && KIND.equals(request.kind())
                    && expectedUid.equals(request.uid()) && expectedJobId.equals(request.jobId());
        } catch (IOException exception) {
            return false;
        }
    }

    private ArtifactLaunchRequest readAndValidate(
            Path requestFile,
            String expectedUid,
            String expectedJobId,
            ArtifactLaunchOperation expectedOperation,
            boolean requireUnexpired) throws IOException {
        ArtifactLaunchRequest request = objectMapper.readValue(
                requestFile.toFile(), ArtifactLaunchRequest.class);
        if (request.version() != VERSION || !KIND.equals(request.kind())) {
            throw new IllegalStateException("unsupported launch request format");
        }
        if (requireUnexpired && !Instant.parse(request.expiresAtUtc()).isAfter(clock.instant())) {
            throw new IllegalStateException("launch request expired");
        }
        if ((expectedUid != null && !expectedUid.equals(request.uid()))
                || (expectedJobId != null && !expectedJobId.equals(request.jobId()))
                || (expectedOperation != null && expectedOperation != request.operation())) {
            throw new IllegalStateException("launch request claims do not match host operation");
        }
        return request;
    }

    private void writeAtomically(Path target, ArtifactLaunchRequest request) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        objectMapper.writeValue(temporary.toFile(), request);
        moveAtomically(temporary, target);
    }

    private Path requestRoot() {
        return betterGiRoot.get().toAbsolutePath().normalize()
                .resolve(Path.of("User", "launch-requests", "artifact-analysis"));
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
