-- 前置：若依及 Quartz 已初始化或经预检显式接管。
-- 仅注册管理员临时解锁权限；不修改账号状态、密码或锁定记录。
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '解除临时锁定', 100, 8, 'F', '0', '0', 'system:user:unlock', '#', 'migration', CURRENT_TIMESTAMP,
       '仅解除密码失败锁定，不启用停用账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:user:unlock');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r JOIN sys_menu m ON m.perms = 'system:user:unlock'
WHERE r.role_key = 'admin' AND r.status = '0' AND r.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);
