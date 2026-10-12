-- P1-19：前置 V2026101104；将已存在汇率列表权限注册为基础资料页面，保留权限ID。
-- 不写入汇率或资金数据；回退应用保留权限/表，按前向修复处理DDL失败。
UPDATE sys_menu page JOIN sys_menu root ON root.parent_id=0 AND root.path='erp' AND root.menu_type='M'
SET page.parent_id=root.menu_id,page.menu_name='日期汇率',page.order_num=2,page.path='exchange-rate',
    page.component='erp/exchange-rate/index',page.route_name='ErpExchangeRate',page.menu_type='C',
    page.visible='0',page.status='0',page.icon='money',page.remark='手工日期汇率与换算预览'
WHERE page.perms='erp:exchangeRate:list' AND page.menu_type='F';

UPDATE sys_menu action JOIN sys_menu page ON page.perms='erp:exchangeRate:list' AND page.menu_type='C'
SET action.parent_id=page.menu_id,action.visible='0'
WHERE action.menu_type='F' AND action.perms IN ('erp:exchangeRate:query','erp:exchangeRate:add','erp:exchangeRate:edit');

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.role_id,root.menu_id
FROM sys_role r JOIN sys_role_menu granted ON granted.role_id=r.role_id
JOIN sys_menu page ON page.menu_id=granted.menu_id AND page.perms='erp:exchangeRate:list'
JOIN sys_menu root ON root.menu_id=page.parent_id
WHERE r.status='0' AND r.del_flag='0'
AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=r.role_id AND existing.menu_id=root.menu_id);
