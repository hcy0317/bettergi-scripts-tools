package com.cloud_guest.cultivation.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloud_guest.mp.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("cultivation_execution_action")
public class CultivationExecutionActionEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("uid")
    private String uid;
    @TableField("plan_revision")
    private Integer planRevision;
    @TableField("executor_id")
    private String executorId;
    @TableField(value = "lease_key", updateStrategy = FieldStrategy.ALWAYS)
    private String leaseKey;
    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;
    @TableField("status")
    private String status;
    @TableField("action_type")
    private String actionType;
    @TableField("material_name")
    private String materialName;
    @TableField("remaining_before")
    private Long remainingBefore;
    @TableField("plan_json")
    private String planJson;
    @TableField("observed_owned")
    private Long observedOwned;
    @TableField("rewards_json")
    private String rewardsJson;
    @TableField("termination_reason")
    private String terminationReason;
    @TableField("result_idempotency_key")
    private String resultIdempotencyKey;
}
