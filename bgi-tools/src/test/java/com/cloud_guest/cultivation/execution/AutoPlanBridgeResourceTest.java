package com.cloud_guest.cultivation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    void windowsInstalledBytesUseTheDeclaredUtf8LfFingerprint() throws Exception {
        byte[] bridge;
        byte[] manifest;
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/cultivation_plan.js")) {
            assertThat(input).isNotNull();
            bridge = input.readAllBytes();
        }
        try (var input = getClass().getResourceAsStream("/cultivation/autoplan/bridge-source.json")) {
            assertThat(input).isNotNull();
            manifest = input.readAllBytes();
        }
        var mapper = new ObjectMapper();
        var declared = mapper.readTree(manifest);
        assertThat(declared.path("normalization").asText()).isEqualTo("utf8-lf");
        String expectedSha = declared.path("sha256").asText();
        String lf = new String(bridge, StandardCharsets.UTF_8);
        assertThat(lf).doesNotContain("\r").contains("\n");
        assertThat(AutoPlanBridgeResource.validate(bridge, manifest, mapper).sha256()).isEqualTo(expectedSha);

        byte[] windows = lf.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        assertThat(new String(windows, StandardCharsets.UTF_8)).contains("\r\n");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(windows)))
                .isNotEqualTo(expectedSha);
        byte[] changed = windows.clone();
        changed[0] ^= 1;
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(changed, manifest, mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("摘要");
        byte[] extraSpace = (" " + lf).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(extraSpace, manifest, mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("摘要");
        ObjectNode unownedSource = (ObjectNode) mapper.readTree(manifest);
        unownedSource.put("sourcePath", "repo/js/Unowned/main.js");
        byte[] unownedManifest = mapper.writeValueAsBytes(unownedSource);
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(bridge, unownedManifest, mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("来源清单");
        byte[] oversizedBridge = new byte[1024 * 1024 + 1];
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(oversizedBridge, manifest, mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("大小边界");
        byte[] oversizedManifest = new byte[16 * 1024 + 1];
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(bridge, oversizedManifest, mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("大小边界");

        byte[] bomWindows = ("\uFEFF" + new String(windows, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        var bomResult = AutoPlanBridgeResource.validate(bomWindows, manifest, mapper);
        assertThat(bomResult.sha256()).isEqualTo(expectedSha);
        assertThat(bomResult.script()).isEqualTo(lf);
        byte[] carriageOnly = lf.replace("\n", "\r").getBytes(StandardCharsets.UTF_8);
        var carriageResult = AutoPlanBridgeResource.validate(carriageOnly, manifest, mapper);
        assertThat(carriageResult.sha256()).isEqualTo(expectedSha);
        assertThat(carriageResult.script()).isEqualTo(lf);
        byte[] malformedUtf8 = {(byte) 0xC3, (byte) 0x28};
        assertThatThrownBy(() -> AutoPlanBridgeResource.validate(malformedUtf8, manifest, mapper))
                .isInstanceOf(IOException.class);

        // Same canonical source and manifest, with only the Windows installed line endings changed.
        assertThat(AutoPlanBridgeResource.validate(windows, manifest, mapper).sha256()).isEqualTo(expectedSha);
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
