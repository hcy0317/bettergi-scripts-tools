CREATE TABLE IF NOT EXISTS `auto_plan_config` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `uid`                    VARCHAR(64)  DEFAULT NULL COMMENT '用户唯一标识',
    `col_order`                  INT          DEFAULT NULL COMMENT '排序',
    `days`                   VARCHAR(255) DEFAULT NULL COMMENT '执行日期（逗号分隔）',
    `day_name`               VARCHAR(255) DEFAULT NULL COMMENT '日期名称',
    `selected_type`          VARCHAR(255) DEFAULT NULL COMMENT '选中类型',
    `run_type`               VARCHAR(255) DEFAULT NULL COMMENT '运行类型',
    `enable`                 TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    `record`                 TINYINT(1)   DEFAULT 0 COMMENT '是否记录',
    `cultivate`                 TINYINT(1)   DEFAULT 0 COMMENT '是培养计划',
    `json`             TEXT         DEFAULT NULL COMMENT 'JSON配置',
    `auto_fight`             TEXT         DEFAULT NULL COMMENT '秘境配置',
    `auto_ley_line_outcrop`  TEXT         DEFAULT NULL COMMENT '自动地脉花配置',
    `auto_stygian_onslaught` TEXT         DEFAULT NULL COMMENT '自动幽境配置',
    `auto_boss` TEXT         DEFAULT NULL COMMENT '自动Boss配置',
    -- ↓ 通用审计字段 ↓
    `create_by`              VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`            TIMESTAMP     DEFAULT NULL COMMENT '创建时间',
    `update_by`              VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`            TIMESTAMP     DEFAULT NULL COMMENT '更新时间',
    `remark`                 TEXT         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动执行计划配置表';

CREATE TABLE IF NOT EXISTS `ws_proxy_access_config` (
    `uid`         VARCHAR(64)  NOT NULL COMMENT '主键（用户标识）',
    `action`      VARCHAR(64)  DEFAULT NULL COMMENT '操作类型',
    `ws_url`      VARCHAR(500) DEFAULT NULL COMMENT 'WebSocket地址',
    `proxy_url`   VARCHAR(500) DEFAULT NULL COMMENT 'WebSocket代理地址',
    `ws_token`    VARCHAR(255) DEFAULT NULL COMMENT '授权Token',
    `at_list`     VARCHAR(500) DEFAULT NULL COMMENT 'AT列表',
    `user_id`     VARCHAR(64)  DEFAULT NULL COMMENT '用户ID',
    `group_id`    VARCHAR(64)  DEFAULT NULL COMMENT '群ID',
    -- 通用审计字段
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time` TIMESTAMP     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time` TIMESTAMP     DEFAULT NULL COMMENT '更新时间',
    `remark`      TEXT         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`uid`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebSocket代理接入配置表';

CREATE TABLE IF NOT EXISTS `auto_plan_uid_global_config`
(
    `uid`         varchar(64) NOT NULL COMMENT '用户唯一标识',
    `cultivate`   TINYINT(1) DEFAULT 0 COMMENT '是培养计划',
    -- 通用审计字段
    `create_by`   VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `create_time` TIMESTAMP   DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `update_time` TIMESTAMP   DEFAULT NULL COMMENT '更新时间',
    `remark`      TEXT        DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全局UID自动计划配置表';

CREATE TABLE IF NOT EXISTS `uid_info_config`
(
    `uid`         varchar(64) NOT NULL COMMENT '用户唯一标识',
    `col_as`          varchar(64) DEFAULT NULL COMMENT 'AS字段（注意：as是保留字，需反引号）',
    `username`       varchar(255) DEFAULT NULL COMMENT '用户名',
    `password`       varchar(255) DEFAULT NULL COMMENT '密码',
    `salt`       varchar(255) DEFAULT NULL COMMENT '盐值',
    `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认UID',
    -- 通用审计字段
    `create_by`   VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `create_time` TIMESTAMP   DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `update_time` TIMESTAMP   DEFAULT NULL COMMENT '更新时间',
    `remark`      TEXT        DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='UID信息配置表';

CREATE TABLE IF NOT EXISTS `db_kv` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type`        VARCHAR(64)  DEFAULT NULL COMMENT '键值类型',
    `key_name`         VARCHAR(128) NOT NULL COMMENT '键名',
    `value`       TEXT         DEFAULT NULL COMMENT '键值',
    -- 通用审计字段
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time` TIMESTAMP     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time` TIMESTAMP     DEFAULT NULL COMMENT '更新时间',
    `remark`      TEXT         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_type_key` (`type`, `key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用键值对存储表';

CREATE TABLE IF NOT EXISTS `sys_job` (
                           `job_id` BIGINT NOT NULL COMMENT '任务ID',
                           `job_name` VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
                           `job_group` VARCHAR(64) DEFAULT NULL COMMENT '任务组名',
                           `invoke_target` VARCHAR(500) NOT NULL COMMENT '调用目标字符串',
                           `cron_expression` VARCHAR(255) DEFAULT NULL COMMENT 'cron执行表达式',
                           `misfire_policy` CHAR(1) DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
                           `concurrent` CHAR(1) DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
                           `status` CHAR(1) DEFAULT '0' COMMENT '状态（0正常 1暂停）',
    -- 通用审计字段
                           `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
                           `create_time` TIMESTAMP     DEFAULT NULL COMMENT '创建时间',
                           `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
                           `update_time` TIMESTAMP     DEFAULT NULL COMMENT '更新时间',
                           `remark`      TEXT         DEFAULT NULL COMMENT '备注',
                           PRIMARY KEY (`job_id`),
                           KEY `idx_job_group_status` (`job_group`, `status`),
                           KEY `idx_job_name` (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务调度表';

-- ... existing code ...

CREATE TABLE IF NOT EXISTS `backup_info` (
                                             `id`            BIGINT       NOT NULL COMMENT '主键',
                                             `backup_name`   VARCHAR(255) DEFAULT NULL COMMENT '备份名称',
                                             `backup_path`   VARCHAR(500) DEFAULT NULL COMMENT '备份路径',
                                             `backup_json`   LONGTEXT     DEFAULT NULL COMMENT '备份信息',
                                             `backup_time`   TIMESTAMP    DEFAULT NULL COMMENT '备份时间',
                                             `backup_size`   BIGINT       DEFAULT NULL COMMENT '备份大小',
    -- 通用审计字段
                                             `create_by`     VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
                                             `create_time`   TIMESTAMP    DEFAULT NULL COMMENT '创建时间',
                                             `update_by`     VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
                                             `update_time`   TIMESTAMP    DEFAULT NULL COMMENT '更新时间',
                                             `remark`        TEXT         DEFAULT NULL COMMENT '备注',
                                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份信息表';

CREATE TABLE IF NOT EXISTS `cultivation_import_preview` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `uid` VARCHAR(64) NOT NULL,
    `image_sha256` CHAR(64) NOT NULL,
    `engine_version` VARCHAR(128) NOT NULL,
    `model_source` VARCHAR(128) NOT NULL,
    `image_width` INT NOT NULL,
    `image_height` INT NOT NULL,
    `raw_ocr_json` LONGTEXT NOT NULL,
    `parsed_json` LONGTEXT NOT NULL,
    `warnings_json` TEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `plan_revision_id` BIGINT NULL,
    `create_by` VARCHAR(64),
    `create_time` DATETIME,
    `update_by` VARCHAR(64),
    `update_time` DATETIME,
    `remark` TEXT,
    INDEX `idx_cultivation_preview_uid_status` (`uid`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养成计算器导入预览';

CREATE TABLE IF NOT EXISTS `cultivation_plan_revision` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `uid` VARCHAR(64) NOT NULL,
    `revision` INT NOT NULL,
    `state` VARCHAR(32) NOT NULL,
    `catalog_version` VARCHAR(64) NOT NULL,
    `preview_id` BIGINT NOT NULL,
    `source_image_sha256` CHAR(64) NOT NULL,
    `engine_version` VARCHAR(128) NOT NULL,
    `model_source` VARCHAR(128) NOT NULL,
    `requirements_json` LONGTEXT NOT NULL,
    `create_by` VARCHAR(64),
    `create_time` DATETIME,
    `update_by` VARCHAR(64),
    `update_time` DATETIME,
    `remark` TEXT,
    UNIQUE KEY `uk_cultivation_revision_uid` (`uid`, `revision`),
    INDEX `idx_cultivation_revision_uid` (`uid`, `revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养成材料账本版本';

CREATE TABLE IF NOT EXISTS `cultivation_module_config` (
    `id` bigint NOT NULL,
    `uid` varchar(64) NOT NULL,
    `module_id` varchar(128) NOT NULL,
    `adapter_version` varchar(32) NOT NULL,
    `enabled` tinyint(1) DEFAULT 1,
    `settings_json` text NOT NULL,
    `create_by` varchar(64) DEFAULT NULL,
    `create_time` timestamp DEFAULT NULL,
    `update_by` varchar(64) DEFAULT NULL,
    `update_time` timestamp DEFAULT NULL,
    `remark` text DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cultivation_module_uid` (`uid`, `module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养成脚本模块设置';

CREATE TABLE IF NOT EXISTS `cultivation_execution_action` (
    `id` varchar(36) NOT NULL,
    `uid` varchar(64) NOT NULL,
    `plan_revision` int NOT NULL,
    `executor_id` varchar(128) NOT NULL,
    `lease_key` varchar(128) DEFAULT NULL,
    `lease_expires_at` datetime DEFAULT NULL,
    `status` varchar(32) NOT NULL,
    `action_type` varchar(32) NOT NULL,
    `material_name` varchar(128) NOT NULL,
    `remaining_before` bigint NOT NULL,
    `plan_json` longtext NOT NULL,
    `observed_owned` bigint DEFAULT NULL,
    `rewards_json` longtext,
    `termination_reason` varchar(128) DEFAULT NULL,
    `result_idempotency_key` varchar(128) DEFAULT NULL,
    `create_by` varchar(64) DEFAULT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    `remark` text,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cultivation_action_lease` (`lease_key`),
    UNIQUE KEY `uk_cultivation_action_result` (`result_idempotency_key`),
    KEY `idx_cultivation_action_uid_revision` (`uid`, `plan_revision`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养成计划驱动行动与权威库存观察';
