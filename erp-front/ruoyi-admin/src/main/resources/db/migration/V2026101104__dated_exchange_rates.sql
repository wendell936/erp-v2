-- P1-18.a：前置 V2026101103；只新增日期汇率及不可覆盖的版本审计，无资金/费用/库存影响。
-- DATE 不转换时区，审计 DATETIME(3) 明确 UTC；1 USD = usd_cny CNY，最多8位小数。
-- 不允许改日期或删除；改值新增修订。DDL不自动回滚，应用回退保留表及历史。
CREATE TABLE erp_exchange_rate (
    rate_id BIGINT NOT NULL AUTO_INCREMENT,
    rate_date DATE NOT NULL,
    usd_cny DECIMAL(16,8) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL COMMENT 'UTC',
    update_by VARCHAR(64) NULL,
    update_time DATETIME(3) NULL COMMENT 'UTC',
    PRIMARY KEY (rate_id),
    UNIQUE KEY uk_exchange_rate_date (rate_date),
    UNIQUE KEY uk_exchange_rate_request (request_id),
    CONSTRAINT ck_exchange_rate_positive CHECK (usd_cny > 0),
    CONSTRAINT ck_exchange_rate_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_exchange_rate_revision (
    revision_id BIGINT NOT NULL AUTO_INCREMENT,
    rate_id BIGINT NOT NULL,
    rate_date DATE NOT NULL,
    usd_cny DECIMAL(16,8) NOT NULL,
    version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    change_time DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (revision_id),
    UNIQUE KEY uk_exchange_rate_revision (rate_id,version),
    CONSTRAINT fk_exchange_revision_rate FOREIGN KEY (rate_id) REFERENCES erp_exchange_rate(rate_id),
    CONSTRAINT ck_exchange_revision_positive CHECK (usd_cny > 0),
    CONSTRAINT ck_exchange_revision_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 当前仅后端权限资源，实际页面另由增量迁移注册。沿用已确认业务角色矩阵。
INSERT INTO sys_menu(menu_name,parent_id,order_num,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT p.label,0,0,'F','1','0',p.perm,'#','migration',CURRENT_TIMESTAMP,'日期汇率后端权限'
FROM (
    SELECT '汇率列表' label,'erp:exchangeRate:list' perm UNION ALL
    SELECT '汇率详情与预览','erp:exchangeRate:query' UNION ALL
    SELECT '汇率新增','erp:exchangeRate:add' UNION ALL
    SELECT '汇率修改','erp:exchangeRate:edit'
) p WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=p.perm);

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms LIKE 'erp:exchangeRate:%'
WHERE r.status='0' AND r.del_flag='0'
AND (r.role_key IN ('admin','erp_operator') OR
     (r.role_key='erp_viewer' AND m.perms IN ('erp:exchangeRate:list','erp:exchangeRate:query')))
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
