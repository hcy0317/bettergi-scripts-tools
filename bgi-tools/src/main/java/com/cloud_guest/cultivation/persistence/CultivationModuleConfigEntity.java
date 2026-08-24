package com.cloud_guest.cultivation.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloud_guest.mp.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cultivation_module_config")
public class CultivationModuleConfigEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("uid")
    private String uid;

    @TableField("module_id")
    private String moduleId;

    @TableField("adapter_version")
    private String adapterVersion;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("settings_json")
    private String settingsJson;
}
