package com.cloud_guest.cultivation.ocr;

import java.time.Duration;
import java.util.List;

public interface CultivationOcrProcess {
    CultivationOcrProcessResult run(List<String> command, Duration timeout) throws Exception;
}
