package com.cloud_guest.cultivation.ocr;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class SystemCultivationOcrProcess implements CultivationOcrProcess {
    @Override
    public CultivationOcrProcessResult run(List<String> command, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PYTHONUTF8", "1");
        Process process = builder.start();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdout = executor.submit(() -> read(process.getInputStream()));
            Future<String> stderr = executor.submit(() -> read(process.getErrorStream()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new CultivationOcrException("PP-OCRv6 识别超时（" + timeout.toSeconds() + " 秒）");
            }
            return new CultivationOcrProcessResult(process.exitValue(), stdout.get(), stderr.get());
        }
    }

    private static String read(InputStream stream) throws Exception {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
