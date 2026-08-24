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
@TableName("cultivation_plan_revision")
public class CultivationPlanRevisionEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("uid")
    private String uid;
    @TableField("revision")
    private Integer revision;
    @TableField("state")
    private String state;
    @TableField("catalog_version")
    private String catalogVersion;
    @TableField("preview_id")
    private Long previewId;
    @TableField("source_image_sha256")
    private String sourceImageSha256;
    @TableField("engine_version")
    private String engineVersion;
    @TableField("model_source")
    private String modelSource;
    @TableField("requirements_json")
    private String requirementsJson;
}
