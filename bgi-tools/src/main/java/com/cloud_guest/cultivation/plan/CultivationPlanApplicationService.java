package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.CultivationUid;
import com.cloud_guest.cultivation.ocr.CultivationCalculatorParser;
import com.cloud_guest.cultivation.ocr.CultivationOcrEngine;
import com.cloud_guest.cultivation.ocr.CultivationOcrResult;
import com.cloud_guest.cultivation.ocr.CultivationParseResult;
import com.cloud_guest.cultivation.ocr.CultivationRequirementRow;
import com.cloud_guest.cultivation.persistence.CultivationImportPreviewEntity;
import com.cloud_guest.cultivation.persistence.CultivationImportPreviewMapper;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionEntity;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
public class CultivationPlanApplicationService {
    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final String DRAFT = "DRAFT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String IMPORTED = "IMPORTED";
    private static final String CATALOG_VERSION = "name-only-v1";

    private final CultivationOcrEngine ocrEngine;
    private final CultivationCalculatorParser parser;
    private final CultivationImportConfirmationAssembler confirmationAssembler;
    private final CultivationImportPreviewMapper previewMapper;
    private final CultivationPlanRevisionMapper revisionMapper;
    private final ObjectMapper objectMapper;

    public CultivationPlanApplicationService(CultivationOcrEngine ocrEngine,
                                             CultivationCalculatorParser parser,
                                             CultivationImportConfirmationAssembler confirmationAssembler,
                                             CultivationImportPreviewMapper previewMapper,
                                             CultivationPlanRevisionMapper revisionMapper,
                                             ObjectMapper objectMapper) {
        this.ocrEngine = ocrEngine;
        this.parser = parser;
        this.confirmationAssembler = confirmationAssembler;
        this.previewMapper = previewMapper;
        this.revisionMapper = revisionMapper;
        this.objectMapper = objectMapper;
    }

    public CultivationImportPreviewResponse preview(String uid, MultipartFile file) {
        String normalizedUid = CultivationUid.normalize(uid);
        validateUpload(file);
        Path temporaryImage = null;
        try {
            temporaryImage = Files.createTempFile("cultivation-calculator-", suffix(file.getOriginalFilename()));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, temporaryImage, StandardCopyOption.REPLACE_EXISTING);
            }

            CultivationOcrResult ocrResult = ocrEngine.recognize(temporaryImage);
            CultivationParseResult parseResult = parser.parse(ocrResult.blocks());
            String imageSha256 = HexFormat.of().formatHex(digest.digest());

            CultivationImportPreviewEntity entity = new CultivationImportPreviewEntity();
            entity.setUid(normalizedUid);
            entity.setImageSha256(imageSha256);
            entity.setEngineVersion(ocrResult.engineVersion());
            entity.setModelSource(ocrResult.modelSource());
            entity.setImageWidth(ocrResult.imageWidth());
            entity.setImageHeight(ocrResult.imageHeight());
            entity.setRawOcrJson(objectMapper.writeValueAsString(ocrResult));
            entity.setParsedJson(objectMapper.writeValueAsString(parseResult.requirements()));
            entity.setWarningsJson(objectMapper.writeValueAsString(parseResult.warnings()));
            entity.setStatus(DRAFT);
            previewMapper.insert(entity);

            return toPreviewResponse(entity, parseResult.requirements(), parseResult.warnings());
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("养成计算器图片导入失败", exception);
        } finally {
            if (temporaryImage != null) {
                try {
                    Files.deleteIfExists(temporaryImage);
                } catch (IOException ignored) {
                    temporaryImage.toFile().deleteOnExit();
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationPlanRevisionResponse confirm(ConfirmCultivationImportRequest request) {
        String normalizedUid = CultivationUid.normalize(request.uid());
        CultivationImportPreviewEntity preview = previewMapper.selectById(request.previewId());
        if (preview == null) {
            throw new IllegalArgumentException("导入预览不存在或已失效");
        }
        if (!preview.getUid().equals(normalizedUid)) {
            throw new IllegalArgumentException("导入预览与 UID 不匹配");
        }
        if (CONFIRMED.equals(preview.getStatus()) && preview.getPlanRevisionId() != null) {
            CultivationPlanRevisionEntity existing = revisionMapper.selectById(preview.getPlanRevisionId());
            if (existing != null) {
                return toRevisionResponse(existing);
            }
        }
        if (!DRAFT.equals(preview.getStatus())) {
            throw new IllegalArgumentException("导入预览状态不可确认：" + preview.getStatus());
        }

        try {
            List<CultivationRequirementRow> originalRows = objectMapper.readValue(
                    preview.getParsedJson(), new TypeReference<>() {
                    });
            List<CultivationLedgerEntry> ledger = confirmationAssembler.assemble(
                    originalRows, request.requirements());
            Integer currentRevision = revisionMapper.findMaxRevision(preview.getUid());

            CultivationPlanRevisionEntity revision = new CultivationPlanRevisionEntity();
            revision.setUid(preview.getUid());
            revision.setRevision((currentRevision == null ? 0 : currentRevision) + 1);
            revision.setState(IMPORTED);
            revision.setCatalogVersion(CATALOG_VERSION);
            revision.setPreviewId(preview.getId());
            revision.setSourceImageSha256(preview.getImageSha256());
            revision.setEngineVersion(preview.getEngineVersion());
            revision.setModelSource(preview.getModelSource());
            revision.setRequirementsJson(objectMapper.writeValueAsString(ledger));
            revisionMapper.insert(revision);

            preview.setStatus(CONFIRMED);
            preview.setPlanRevisionId(revision.getId());
            previewMapper.updateById(preview);
            return toRevisionResponse(revision, ledger);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取导入预览", exception);
        }
    }

    public CultivationPlanRevisionResponse latest(String uid) {
        CultivationPlanRevisionEntity entity = revisionMapper.findLatest(CultivationUid.normalize(uid));
        return entity == null ? null : toRevisionResponse(entity);
    }

    private CultivationImportPreviewResponse toPreviewResponse(CultivationImportPreviewEntity entity,
                                                                List<CultivationRequirementRow> rows,
                                                                List<String> warnings) {
        List<CultivationPreviewRow> previewRows = IntStream.range(0, rows.size())
                .mapToObj(index -> CultivationPreviewRow.from(index, rows.get(index)))
                .toList();
        return new CultivationImportPreviewResponse(
                entity.getId(), entity.getUid(), entity.getImageSha256(), entity.getEngineVersion(),
                entity.getModelSource(), entity.getImageWidth(), entity.getImageHeight(), previewRows, warnings);
    }

    private CultivationPlanRevisionResponse toRevisionResponse(CultivationPlanRevisionEntity entity) {
        try {
            List<CultivationLedgerEntry> ledger = objectMapper.readValue(
                    entity.getRequirementsJson(), new TypeReference<>() {
                    });
            return toRevisionResponse(entity, ledger);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取养成计划 revision", exception);
        }
    }

    private static CultivationPlanRevisionResponse toRevisionResponse(CultivationPlanRevisionEntity entity,
                                                                       List<CultivationLedgerEntry> ledger) {
        return new CultivationPlanRevisionResponse(
                entity.getId(), entity.getUid(), entity.getRevision(), entity.getState(),
                entity.getCatalogVersion(), entity.getPreviewId(), entity.getSourceImageSha256(),
                entity.getEngineVersion(), entity.getModelSource(), ledger, entity.getCreateTime());
    }

    private static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择养成计算器导出的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 25 MB");
        }
        String contentType = file.getContentType();
        boolean imageContentType = contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        boolean imageFilename = hasImageExtension(file.getOriginalFilename());
        if (!imageContentType && !imageFilename) {
            throw new IllegalArgumentException("上传文件必须是图片");
        }
    }

    private static boolean hasImageExtension(String filename) {
        if (filename == null) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".png")
                || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".webp")
                || normalized.endsWith(".bmp");
    }

    private static String suffix(String filename) {
        if (filename == null) {
            return ".img";
        }
        int separator = filename.lastIndexOf('.');
        if (separator < 0 || separator == filename.length() - 1) {
            return ".img";
        }
        String suffix = filename.substring(separator).toLowerCase(Locale.ROOT);
        return suffix.matches("\\.[a-z0-9]{1,8}") ? suffix : ".img";
    }
}
