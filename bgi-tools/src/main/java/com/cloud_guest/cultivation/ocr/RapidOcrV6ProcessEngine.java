package com.cloud_guest.cultivation.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class RapidOcrV6ProcessEngine implements CultivationOcrEngine {
    private static final String DET_MODEL = "Assets/Model/PaddleOCR/Det/V6/PP-OCRv6_small_det_infer/slim.onnx";
    private static final String REC_MODEL = "Assets/Model/PaddleOCR/Rec/V6/PP-OCRv6_small_rec_infer/slim.onnx";

    private final CultivationOcrProcess process;
    private final ObjectMapper objectMapper;
    private final CultivationOcrProperties properties;
    private final Path bridgeScript;
    private final Path betterGiRoot;

    @Autowired
    public RapidOcrV6ProcessEngine(CultivationOcrProcess process,
                                   ObjectMapper objectMapper,
                                   CultivationOcrProperties properties) {
        this(process, objectMapper, properties, extractBridgeScript(), locateBetterGiRoot(properties));
    }

    RapidOcrV6ProcessEngine(CultivationOcrProcess process,
                            ObjectMapper objectMapper,
                            CultivationOcrProperties properties,
                            Path bridgeScript,
                            Path betterGiRoot) {
        this.process = process;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.bridgeScript = bridgeScript;
        this.betterGiRoot = betterGiRoot;
    }

    @Override
    public CultivationOcrResult recognize(Path imagePath) {
        List<String> command = new ArrayList<>(List.of(
                properties.getPythonCommand(),
                bridgeScript.toAbsolutePath().toString(),
                "--input", imagePath.toAbsolutePath().toString()
        ));
        if (betterGiRoot != null) {
            command.add("--bettergi-root");
            command.add(betterGiRoot.toAbsolutePath().toString());
        }

        try {
            CultivationOcrProcessResult output = process.run(command, properties.getTimeout());
            if (output.exitCode() != 0) {
                String detail = output.stderr() == null || output.stderr().isBlank()
                        ? output.stdout()
                        : output.stderr();
                throw new CultivationOcrException("PP-OCRv6 识别进程失败：" + detail.trim());
            }
            CultivationOcrResult result = objectMapper.readValue(output.stdout(), CultivationOcrResult.class);
            if (result.engineVersion() == null || !result.engineVersion().contains("PP-OCRv6")) {
                throw new CultivationOcrException("OCR 进程未返回 PP-OCRv6 版本信息");
            }
            return result;
        } catch (CultivationOcrException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CultivationOcrException("无法执行 PP-OCRv6 识别", exception);
        }
    }

    private static Path extractBridgeScript() {
        Path target = Path.of(System.getProperty("java.io.tmpdir"),
                "bettergi-scripts-tools", "cultivation-ocr", "rapidocr_v6_bridge.py");
        try {
            Files.createDirectories(target.getParent());
            try (var source = new ClassPathResource("ocr/rapidocr_v6_bridge.py").getInputStream()) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException exception) {
            throw new CultivationOcrException("无法释放 PP-OCRv6 桥接脚本", exception);
        }
    }

    private static Path locateBetterGiRoot(CultivationOcrProperties properties) {
        if (properties.getBettergiRoot() != null && !properties.getBettergiRoot().isBlank()) {
            Path configured = Path.of(properties.getBettergiRoot()).toAbsolutePath().normalize();
            return hasV6Models(configured) ? configured : null;
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            if (hasV6Models(current)) {
                return current;
            }
        }
        return null;
    }

    private static boolean hasV6Models(Path root) {
        return Files.isRegularFile(root.resolve(DET_MODEL)) && Files.isRegularFile(root.resolve(REC_MODEL));
    }
}
