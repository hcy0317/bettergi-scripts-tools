package com.cloud_guest.cultivation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 消费固定源码产物，不维护第二套桥接实现。 */
record AutoPlanBridgeResource(String script, String manifest, String sha256) {
    static AutoPlanBridgeResource load(ObjectMapper mapper) throws IOException {
        try (var script = AutoPlanBridgeResource.class.getResourceAsStream("/cultivation/autoplan/cultivation_plan.js");
             var manifest = AutoPlanBridgeResource.class.getResourceAsStream("/cultivation/autoplan/bridge-source.json")) {
            if (script == null || manifest == null) throw new IllegalStateException("计划驱动桥接或来源清单缺失");
            return validate(script.readNBytes(1024 * 1024 + 1), manifest.readNBytes(16 * 1024 + 1), mapper);
        }
    }

    static AutoPlanBridgeResource validate(byte[] script, byte[] manifest, ObjectMapper mapper) throws IOException {
        if (script.length > 1024 * 1024 || manifest.length > 16 * 1024)
            throw new IllegalStateException("计划驱动桥接资源超出大小边界");
        var source = mapper.readTree(manifest);
        if (source == null || source.path("schemaVersion").asInt() != 1
                || !"https://github.com/hcy0317/bettergi-scripts-list".equals(source.path("repository").asText())
                || !"repo/js/AutoPlan/utils/cultivation_plan.js".equals(source.path("sourcePath").asText())
                || !"utf8-lf".equals(source.path("normalization").asText()))
            throw new IllegalStateException("计划驱动桥接来源清单不受支持");
        String actual;
        try {
            actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法核对桥接摘要", exception);
        }
        if (!actual.equals(source.path("sha256").asText()))
            throw new IllegalStateException("计划驱动桥接摘要与主源不一致，禁止安装被改写的资源");
        return new AutoPlanBridgeResource(new String(script, StandardCharsets.UTF_8),
                new String(manifest, StandardCharsets.UTF_8), actual);
    }
}
