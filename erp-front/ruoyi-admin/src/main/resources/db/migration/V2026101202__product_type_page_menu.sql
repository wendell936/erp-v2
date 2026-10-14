-- P1-06.a：前置 V2026101201，只将已有类型权限归入页面，保留授权和业务数据。
UPDATE sys_menu page JOIN sys_menu root ON root.parent_id=0 AND root.path='erp' AND root.menu_type='M'
SET page.parent_id=root.menu_id,page.menu_name='产品类型',page.order_num=3,page.path='product-type',
    page.component='erp/product-type/index',page.route_name='ErpProductType',page.menu_type='C',
    page.visible='0',page.status='0',page.icon='list',page.remark='产品类型字典维护'
WHERE page.perms='erp:productType:list' AND page.menu_type='F';
UPDATE sys_menu action JOIN sys_menu page ON page.perms='erp:productType:list' AND page.menu_type='C'
SET action.parent_id=page.menu_id,action.visible='0'
WHERE action.menu_type='F' AND action.perms IN
    ('erp:productType:query','erp:productType:add','erp:productType:edit','erp:productType:status');
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.role_id,root.menu_id
FROM sys_role r JOIN sys_role_menu granted ON granted.role_id=r.role_id
JOIN sys_menu page ON page.menu_id=granted.menu_id AND page.perms='erp:productType:list'
JOIN sys_menu root ON root.menu_id=page.parent_id
WHERE r.status='0' AND r.del_flag='0'
AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=r.role_id AND existing.menu_id=root.menu_id);
