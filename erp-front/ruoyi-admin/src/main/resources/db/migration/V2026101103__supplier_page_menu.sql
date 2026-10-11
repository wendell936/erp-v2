-- P1-10.a：前置 V2026101102，将既有列表权限提升为真实供应商页面，其余功能归入页面。
-- 保留原权限ID/授权关系；不授予新的业务操作权限，不改业务表和数据。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,icon,create_by,create_time,remark)
SELECT '基础资料',0,5,'erp','M','0','0','tree','migration',CURRENT_TIMESTAMP,'ERP基础资料'
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='erp' AND menu_type='M');

UPDATE sys_menu page JOIN sys_menu root ON root.parent_id=0 AND root.path='erp' AND root.menu_type='M'
SET page.parent_id=root.menu_id,page.menu_name='供应商',page.order_num=1,page.path='supplier',
    page.component='erp/supplier/index',page.route_name='ErpSupplier',page.menu_type='C',
    page.visible='0',page.status='0',page.icon='peoples',page.remark='供应商基础资料维护'
WHERE page.perms='erp:supplier:list' AND page.menu_type='F';

UPDATE sys_menu action JOIN sys_menu page ON page.perms='erp:supplier:list' AND page.menu_type='C'
SET action.parent_id=page.menu_id,action.visible='0'
WHERE action.menu_type='F' AND action.perms IN
    ('erp:supplier:query','erp:supplier:add','erp:supplier:edit','erp:supplier:status');

-- 只有已获列表访问的有效角色获得父目录。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.role_id,root.menu_id
FROM sys_role r JOIN sys_role_menu granted ON granted.role_id=r.role_id
JOIN sys_menu page ON page.menu_id=granted.menu_id AND page.perms='erp:supplier:list'
JOIN sys_menu root ON root.menu_id=page.parent_id
WHERE r.status='0' AND r.del_flag='0'
AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=r.role_id AND existing.menu_id=root.menu_id);
