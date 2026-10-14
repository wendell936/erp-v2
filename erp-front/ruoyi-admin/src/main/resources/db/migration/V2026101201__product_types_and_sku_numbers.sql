-- P1-02.a/P1-03.a/b：前置 V2026101105，只增加类型字典和编号基础，不导入示例或创建商品/库存。
-- UTC DATETIME(3)；类型码与 SKU 大小写不敏感唯一，保留录入拼写；主键独立。
-- 编号只增不提供删除/重置；回退应用保留表。DDL 不自动回滚，失败停止并前向修复/恢复备份。
CREATE TABLE erp_product_type (
    type_id BIGINT NOT NULL AUTO_INCREMENT,
    type_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    type_name VARCHAR(200) NOT NULL,
    keywords VARCHAR(500) NULL,
    status CHAR(1) NOT NULL DEFAULT '0',
    version BIGINT NOT NULL DEFAULT 0,
    last_number BIGINT NOT NULL DEFAULT 0 COMMENT '已分配主序号，不因列表重排或商品删除降低',
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL COMMENT 'UTC',
    update_by VARCHAR(64) NULL,
    update_time DATETIME(3) NULL COMMENT 'UTC',
    remark VARCHAR(500) NULL,
    PRIMARY KEY(type_id),
    UNIQUE KEY uk_product_type_code(type_code),
    UNIQUE KEY uk_product_type_request(request_id),
    KEY idx_product_type_status_id(status,type_id),
    CONSTRAINT ck_product_type_code CHECK (REGEXP_LIKE(type_code,'^[A-Za-z0-9-]+$','c')),
    CONSTRAINT ck_product_type_name CHECK (CHAR_LENGTH(TRIM(type_name))>0),
    CONSTRAINT ck_product_type_status CHECK (status IN ('0','1')),
    CONSTRAINT ck_product_type_counters CHECK (version>=0 AND last_number>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_product_type_change (
    change_id BIGINT NOT NULL AUTO_INCREMENT,
    type_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    operation VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    change_time DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY(change_id),
    UNIQUE KEY uk_product_type_change_version(type_id,version),
    CONSTRAINT fk_product_type_change FOREIGN KEY(type_id) REFERENCES erp_product_type(type_id),
    CONSTRAINT ck_product_type_change_version CHECK (version>=0),
    CONSTRAINT ck_product_type_operation CHECK (operation IN ('CREATE','UPDATE','STATUS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_sku_number (
    number_id BIGINT NOT NULL AUTO_INCREMENT,
    type_id BIGINT NOT NULL,
    main_number_id BIGINT NULL COMMENT '仅编号归属，不决定商品父级是否可售或参与库存',
    sequence_number BIGINT NOT NULL,
    last_variant_number BIGINT NOT NULL DEFAULT 0,
    sku_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY(number_id),
    UNIQUE KEY uk_sku_number_code(sku_code),
    UNIQUE KEY uk_sku_number_request(request_id),
    UNIQUE KEY uk_sku_number_id_type(number_id,type_id),
    UNIQUE KEY uk_sku_number_variant(main_number_id,sequence_number),
    KEY idx_sku_number_type(type_id,number_id),
    CONSTRAINT fk_sku_number_type FOREIGN KEY(type_id) REFERENCES erp_product_type(type_id),
    CONSTRAINT fk_sku_number_main FOREIGN KEY(main_number_id,type_id) REFERENCES erp_sku_number(number_id,type_id),
    CONSTRAINT ck_sku_number_counters CHECK (sequence_number>0 AND last_variant_number>=0),
    CONSTRAINT ck_sku_number_scope CHECK (main_number_id IS NULL OR (main_number_id>0 AND last_variant_number=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 后端资源，页面另用增量迁移注册。已有有效操作角色获写权限，只读角色仅列表/详情。
INSERT INTO sys_menu(menu_name,parent_id,order_num,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT p.label,0,0,'F','1','0',p.perm,'#','migration',CURRENT_TIMESTAMP,'产品类型后端权限'
FROM (
    SELECT '产品类型列表' label,'erp:productType:list' perm UNION ALL
    SELECT '产品类型详情','erp:productType:query' UNION ALL
    SELECT '产品类型新增','erp:productType:add' UNION ALL
    SELECT '产品类型编辑','erp:productType:edit' UNION ALL
    SELECT '产品类型启停','erp:productType:status'
) p WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=p.perm);
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms LIKE 'erp:productType:%'
WHERE r.status='0' AND r.del_flag='0'
AND (r.role_key IN ('admin','erp_operator') OR (r.role_key='erp_viewer' AND m.perms IN ('erp:productType:list','erp:productType:query')))
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
