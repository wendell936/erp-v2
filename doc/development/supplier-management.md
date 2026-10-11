# 供应商基础资料接口

任务 P1-08.a、P1-09.a、P1-10.a，需求[供应-01](../requirements/requirements-checklist.md)。当前实现基础资料接口及页面；商品报价、采购引用及账期/订金尾款计算分别按后续任务实施。

## 输入与业务约束

供应商名称必填、最多200字符；联系人和联系方式各100字符，结算说明1000字符，备注500字符，后三类可空。服务端去除首尾空白、拒绝脚本标签；名称不作为唯一身份，不自动合并同名资料。结算说明仅保存文字，不生成付款计划。

创建必须提供 UUID `requestId`。同一操作人、同一标识、相同标准化内容的重试返回同一供应商，不重复新增或写创建审计；复用标识提交不同内容或由不同操作人提交，均拒绝。原始请求摘要不随后续编辑变化，重试成功返回当前资料；标识和摘要不在响应中返回。

编辑和启停必须提供读取到的 `version` 及非空 `reason`（最多500字符）。版本初始0，每次成功变更加一；只允许0至9223372036854775806作为提交版本，旧版本冲突须刷新核对，禁止自动覆盖。资料、版本和业务审计同一事务，审计失败全部回滚。停用仍可查询/维护历史资料，无物理删除接口。未来采购选择可用供应商的规则在采购任务实施。

状态 `0` 启用、`1` 停用；创建固定启用。服务端只接收允许的资料字段，操作人、时间、状态初始化、请求摘要均由服务端生成。写操作保留操作种类、人员、时间、原因和版本；当前审计不存每版完整联系人快照，也不代表采购单历史快照已经实现。

## HTTP 与权限

沿用若依 `AjaxResult` / `TableDataInfo` 和现有业务错误码约定（业务失败 `code=500`、无权限403、未登录401）。无部门或创建人过滤，执行已确认的[公司级权限矩阵](../business/access-control-matrix.md)。

| 方法与路径 | 参数/返回 | 权限 |
|---|---|---|
| GET `/erp/supplier/list` | 名称包含筛选 `supplierName`、状态 `status`；`rows/total` | `erp:supplier:list` |
| GET `/erp/supplier/{id}` | 当前资料 `data` | `erp:supplier:query` |
| GET `/erp/supplier/{id}/changes` | 变更审计 `rows/total` | `erp:supplier:query` |
| POST `/erp/supplier` | 名称、可选资料、`requestId`；返回当前资料 | `erp:supplier:add` |
| PUT `/erp/supplier/{id}` | 完整资料字段、`version/reason`；可选资料省略视为空 | `erp:supplier:edit` |
| PUT `/erp/supplier/{id}/status` | `status/version/reason` | `erp:supplier:status` |

分页默认10、最多100，供应商默认ID倒序，审计默认版本倒序，客户端排序仍追加服务端唯一键。包含筛选不能承诺利用名称前缀索引；小团队初期沿用当前查询，实际容量与性能待真实规模评估。offset分页不承诺并发增删时快照一致。

通用操作日志保留请求地址、人员、结果；供应商写请求和响应正文不进入该日志，Mapper日志保持INFO以避免DEBUG参数记录。关闭Druid慢SQL原文日志以避免记录绑定参数，保留StatFilter统计和耗时阈值；诊断慢查询时须使用脱敏观测，不直接启用原文参数日志。数据库异常转换为固定业务提示，本地仅记录操作、ID、异常类型、SQLState及数据库错误码，不输出SQL异常原文。业务审计中的原因由用户填写，不应包含密码、令牌或其他密钥。

## 迁移与时间

唯一脚本 [V2026101102__supplier_master.sql](../../erp-front/ruoyi-admin/src/main/resources/db/migration/V2026101102__supplier_master.sql)，前置为 V2026101101。新增 `erp_supplier`、`erp_supplier_change`，前者请求键唯一，后者供应商/版本唯一且物理外键限制删除；同事务保证审计一致性。没有金额字段，也没有新增第三方依赖。

时间点存UTC `DATETIME(3)`，通过显式UTC Calendar的专用MyBatis handler读写，精度毫秒；避免JVM/连接默认时区和MySQL TIMESTAMP的2038年上限。沿用框架Date及当前GMT+8接口展示配置，接口显示字符串不应用于反推外部业务期间。这里没有确认Amazon、广告或结算业务时区。

V2026101102注册5个隐藏功能权限资源；迁移时已有且有效的 `admin/erp_operator` 获全部5项，`erp_viewer` 仅列表和详情；迁移后新建角色须由管理员配置权限。

页面增量 [V2026101103__supplier_page_menu.sql](../../erp-front/ruoyi-admin/src/main/resources/db/migration/V2026101103__supplier_page_menu.sql) 保留权限ID与授权，将列表资源提升为“基础资料 → 供应商”页面（`/erp/supplier`，组件 `erp/supplier/index`），其余4项归入页面，仅给已获列表访问的有效角色补父目录。基础迁移不因页面发布被重写。

部署前备份并核对Flyway历史及既有同名表/权限；只在明确的目标环境执行迁移。旧应用回退时保留新表和权限，不执行删表回退。MySQL DDL不保证整体事务回滚，失败时停止发布、核对成功语句和历史，按[迁移规范](database-migrations.md)前向修复或恢复备份，禁止盲目repair/clean。当前仅运行隔离测试，没有迁移业务库。

## 验证入口

Windows本地执行根目录 `scripts/test-database-migrations.ps1`，使用Docker Desktop创建随机MySQL/Redis实例、随机密码、loopback端口并清理本次容器；不连业务库，不触发GitHub Actions。

`SupplierFlowIT`覆盖实际应用升级、管理员/操作/只读权限、输入和版本边界、创建并发重试及编辑后的重试、并发修改、创建/修改/启停的审计失败回滚、分页和数据库约束、非UTC连接下的UTC持久化、通用审计正文排除。结果与失败恢复见[执行记录 E030](execution-journal.md)，原始日志在 `.local/logs/P1-09.a/`，不提交Git。

页面含名称/状态筛选、分页、新增、编辑、启停和详情变更记录。新增打开时生成请求UUID，在该表单内重试保持不变；保存期间禁用输入、关闭和重复提交，失败保留输入；编辑冲突须重新打开核对，禁止自动换版本重试。前端部署使用HTTPS（本机localhost开发环境亦支持浏览器UUID API）。前端验证及证据见[页面验收](../testing/supplier-management-ui.md)。

真实供应商资料、商品报价/币种/交期、采购引用停用限制、真实数据容量、Java17实际运行及生产发布均不在本批验证结果内。
