package com.cloud_guest.cultivation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoPlanBridgeResourceTest {
    @Test
    void alteredCodeCannotBeAcceptedAsThePinnedBridge() throws Exception {
        byte[] bridge;
        byte[] manifest;
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/cultivation_plan.js")) {
            bridge = input.readAllBytes();
        }
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/bridge-source.json")) {
            manifest = input.readAllBytes();
        }
        assertThat(AutoPlanBridgeResource.validate(bridge, manifest, new ObjectMapper()).script())
                .isEqualTo(new String(bridge, StandardCharsets.UTF_8));
        byte[] changed = (new String(bridge, StandardCharsets.UTF_8) + "// changed\n").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(changed, manifest, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("摘要");
    }

    @Test
    void packagedBridgeKeepsJavascriptLiteralsAndMatchesThePinnedSource() throws Exception {
        byte[] bridge;
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/cultivation_plan.js")) {
            assertThat(input).isNotNull();
            bridge = input.readAllBytes();
        }
        assertThat(new String(bridge, StandardCharsets.UTF_8))
                .contains("`${name}=${observedOwned[name]}`")
                .contains("async function executeCraftAction(");
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/bridge-source.json")) {
            assertThat(input).isNotNull();
            var manifest = new ObjectMapper().readTree(input);
            assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(manifest.path("sha256").asText()).isEqualTo(
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bridge)));
        }
    }
}
