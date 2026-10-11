-- P1-08.a：若依、Quartz、解锁权限基线之后追加；仅新建ERP供应商表，不改既有业务。
-- UTC DATETIME(3) 经专用 MyBatis handler 读写；精度毫秒，审计时间由服务端指定。
-- 供应商名称非唯一；仅创建请求UUID唯一，允许同名主体，不自动合并。
-- 不物理删除，不含报价/采购/资金规则。回退应用保留表；DDL不自动回滚。
CREATE TABLE erp_supplier (
    supplier_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    supplier_name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(100) NULL,
    contact_phone VARCHAR(100) NULL,
    settlement_terms VARCHAR(1000) NULL COMMENT '仅结算说明，不自动生成付款计划',
    status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    version BIGINT NOT NULL DEFAULT 0,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建幂等键',
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始创建内容摘要，不随编辑变化',
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL COMMENT 'UTC',
    update_by VARCHAR(64) NULL,
    update_time DATETIME(3) NULL COMMENT 'UTC',
    remark VARCHAR(500) NULL,
    PRIMARY KEY (supplier_id),
    UNIQUE KEY uk_supplier_request (request_id),
    KEY idx_supplier_status_id (status, supplier_id),
    KEY idx_supplier_name_id (supplier_name, supplier_id),
    CONSTRAINT ck_supplier_status CHECK (status IN ('0','1')),
    CONSTRAINT ck_supplier_version CHECK (version >= 0),
    CONSTRAINT ck_supplier_name CHECK (CHAR_LENGTH(TRIM(supplier_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_supplier_change (
    change_id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    operation VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    change_time DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (change_id),
    UNIQUE KEY uk_supplier_change_version (supplier_id, version),
    CONSTRAINT fk_supplier_change_supplier FOREIGN KEY (supplier_id) REFERENCES erp_supplier(supplier_id),
    CONSTRAINT ck_supplier_change_version CHECK (version >= 0),
    CONSTRAINT ck_supplier_change_operation CHECK (operation IN ('CREATE','UPDATE','STATUS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 本批仅后端权限资源（F），不注册尚未实现的页面；页面任务再用增量迁移归入对应菜单。
INSERT INTO sys_menu(menu_name,parent_id,order_num,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT p.label,0,0,'F','1','0',p.perm,'#','migration',CURRENT_TIMESTAMP,'供应商后端权限'
FROM (
    SELECT '供应商列表' label,'erp:supplier:list' perm UNION ALL
    SELECT '供应商详情','erp:supplier:query' UNION ALL
    SELECT '供应商新增','erp:supplier:add' UNION ALL
    SELECT '供应商编辑','erp:supplier:edit' UNION ALL
    SELECT '供应商启停','erp:supplier:status'
) p
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=p.perm);

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms LIKE 'erp:supplier:%'
WHERE r.status='0' AND r.del_flag='0'
  AND (r.role_key IN ('admin','erp_operator')
       OR (r.role_key='erp_viewer' AND m.perms IN ('erp:supplier:list','erp:supplier:query')))
  AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
