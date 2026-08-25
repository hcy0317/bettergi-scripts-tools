package com.cloud_guest.cultivation;

import java.util.regex.Pattern;

public final class CultivationUid {
    private static final Pattern CANONICAL = Pattern.compile("[0-9]{6,12}");

    private CultivationUid() {
    }

    public static String normalize(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("UID 不能为空");
        }
        String normalized = uid.trim();
        if (!CANONICAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("UID 必须是 6 至 12 位数字");
        }
        return normalized;
    }
}
