-- P1-26，前置V2026101202；类型码仅英文字母数字，连字符保留给SKU层级。
-- 部署前暂停类型/编号写入并备份，预检：
-- SELECT type_id,type_code,last_number FROM erp_product_type
-- WHERE NOT REGEXP_LIKE(type_code,'^[A-Za-z0-9]+$','c');
-- 存在不合法资料时本ALTER失败，停止发布，保留原记录、SKU与旧约束；
-- 不自动改码/删数据，不自动repair。核对历史及实际DDL后按授权前向处理或恢复备份。
-- 单条InnoDB ALTER替换约束，成功后原类型、编号、计数及审计内容不变。
-- 应用回退时保留更严格约束；旧版尝试写入连字符类型会被拒绝。
ALTER TABLE erp_product_type
    DROP CHECK ck_product_type_code,
    ADD CONSTRAINT ck_product_type_code CHECK (REGEXP_LIKE(type_code,'^[A-Za-z0-9]+$','c'));
