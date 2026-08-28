package com.cloud_guest.artifact.launch;

import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactLaunchRequestServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsExpiringHostUriAndConsumesRequestOnlyOnce() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        ArtifactLaunchRequestService service = new ArtifactLaunchRequestService(
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(5));

        ArtifactLaunchResult launch = service.create("102550550", "job-1", ArtifactLaunchOperation.ANALYZE);

        assertThat(launch.launchUri()).matches(
                "^BetterGIArtifact://analysis\\?request=[0-9a-f-]{36}$");
        ArtifactLaunchRequest request = service.consume(launch.requestToken());
        assertThat(request.uid()).isEqualTo("102550550");
        assertThat(request.jobId()).isEqualTo("job-1");
        assertThat(request.operation()).isEqualTo(ArtifactLaunchOperation.ANALYZE);
        assertThat(service.consume(launch.requestToken())).isEqualTo(request);
    }

    @Test
    void expiredRequestCannotBeConsumed() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        ArtifactLaunchRequestService service = new ArtifactLaunchRequestService(
                tempDirectory, new ObjectMapper(), clock, Duration.ofSeconds(30));
        ArtifactLaunchResult launch = service.create("102550550", "job-1", ArtifactLaunchOperation.ANALYZE);
        clock.set(now.plusSeconds(31));

        assertThatThrownBy(() -> service.consume(launch.requestToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void mismatchedClaimsDoNotConsumeTheCapabilityToken() {
        ArtifactLaunchRequestService service = new ArtifactLaunchRequestService(
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ArtifactLaunchResult launch = service.create(
                "102550550", "job-1", ArtifactLaunchOperation.EXECUTE_LOCK_PLAN,
                1, java.util.List.of(), null, null);

        assertThatThrownBy(() -> service.consume(
                launch.requestToken(), "102550550", "other-job",
                ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claims");

        ArtifactLaunchRequest request = service.consume(
                launch.requestToken(), "102550550", "job-1",
                ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        assertThat(request.jobId()).isEqualTo("job-1");
    }

    @Test
    void claimedRequestRemainsAuthorizedForCompletionAfterItsLaunchWindow() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        ArtifactLaunchRequestService service = new ArtifactLaunchRequestService(
                tempDirectory, new ObjectMapper(), clock, Duration.ofSeconds(30));
        ArtifactLaunchResult launch = service.create(
                "102550550", "job-1", ArtifactLaunchOperation.ANALYZE);

        service.consume(
                launch.requestToken(), "102550550", "job-1", ArtifactLaunchOperation.ANALYZE);
        clock.set(now.plusSeconds(31));

        assertThat(service.authorizeClaimed(
                launch.requestToken(), "102550550", "job-1", ArtifactLaunchOperation.ANALYZE))
                .isNotNull();
        assertThat(service.complete(
                launch.requestToken(), "102550550", "job-1", ArtifactLaunchOperation.ANALYZE))
                .isNotNull();
        assertThat(service.complete(
                launch.requestToken(), "102550550", "job-1", ArtifactLaunchOperation.ANALYZE))
                .isNotNull();
    }

    @Test
    void characterRosterRequestBindsTheReviewedActivationSettings() {
        ArtifactLaunchRequestService service = new ArtifactLaunchRequestService(
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));

        ArtifactLaunchResult launch = service.createCharacterRoster(
                "102550550", "job-roster",
                new ArtifactBuildAutoActivationSettings(80, true), "眇", "遥");

        assertThat(launch.launchUri()).matches(
                "^BetterGIArtifact://characters\\?request=[0-9a-f-]{36}$");
        ArtifactLaunchRequest request = service.consume(launch.requestToken());
        assertThat(request.operation()).isEqualTo(ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER);
        assertThat(request.characterLevelThreshold()).isEqualTo(80);
        assertThat(request.favoriteOverride()).isTrue();
        assertThat(request.gameNickname()).isEqualTo("眇");
        assertThat(request.miliastraNickname()).isEqualTo("遥");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
