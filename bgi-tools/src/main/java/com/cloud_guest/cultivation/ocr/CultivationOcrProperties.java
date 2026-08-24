package com.cloud_guest.cultivation.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "cultivation.ocr")
public class CultivationOcrProperties {
    private String pythonCommand = "python";
    private String bettergiRoot = "";
    private Duration timeout = Duration.ofMinutes(2);
}
