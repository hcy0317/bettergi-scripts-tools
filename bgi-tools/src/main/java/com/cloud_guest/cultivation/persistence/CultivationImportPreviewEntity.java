package com.cloud_guest.cultivation.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloud_guest.mp.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("cultivation_import_preview")
public class CultivationImportPreviewEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("uid")
    private String uid;
    @TableField("image_sha256")
    private String imageSha256;
    @TableField("engine_version")
    private String engineVersion;
    @TableField("model_source")
    private String modelSource;
    @TableField("image_width")
    private Integer imageWidth;
    @TableField("image_height")
    private Integer imageHeight;
    @TableField("raw_ocr_json")
    private String rawOcrJson;
    @TableField("parsed_json")
    private String parsedJson;
    @TableField("warnings_json")
    private String warningsJson;
    @TableField("status")
    private String status;
    @TableField("plan_revision_id")
    private Long planRevisionId;
}
