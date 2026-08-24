package com.cloud_guest.cultivation.ocr;

import java.nio.file.Path;

public interface CultivationOcrEngine {
    CultivationOcrResult recognize(Path imagePath);
}
