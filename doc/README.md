# 项目文档索引

## 当前文档

- [云端接续记录](development/cloud-continuation.md)：版本同步及本月 CI 限制。
- [数据库迁移技术决策](architecture/database-migration-decision.md)：Flyway、唯一脚本来源、主库装配、显式基线和恢复。

- [项目开发约定](../AGENTS.md)：企业级开发原则、业务边界、质量要求和工作空间管理规则。
- [业务需求基线](requirements/requirements-checklist.md)：Amazon ERP 的业务目标、流程、规则、推进重点及范围外事项。
- [需求与验收规范](requirements/requirements-workflow.md)：需求描述、变更和完成标准。
- [业务术语、编码与单据规范](business/business-conventions.md)：库存术语、编码和单据纠错边界。
- [账号权限与业务数据范围](business/access-control-matrix.md)：已确认的管理员、操作和只读角色矩阵。
- [编码、数据与接口规范](development/engineering-standards.md)：分层、建模、接口、Git 和环境隔离。
- [ERP 分阶段开发计划](development/development-plan.md)：现有框架评估、ERP 模块组织、阶段依赖、SP-API 对接及验收安排。
- [开发任务台账](development/task-board.md)：细任务 ID、依赖、状态、验收条件和待确认项，跟进进度的入口。
- [开发执行记录](development/execution-journal.md)：按任务关联的实际变更、验证、失败与恢复摘要。
- [数据库版本管理规范](development/database-migrations.md)：初始化、增量迁移及工具接入要求。
- [Amazon SP-API 接入与配置](development/amazon-sp-api-integration.md)：官方 SDK、认证适配、安全配置、依赖兼容及验证边界。
- [供应商基础资料接口](development/supplier-management.md)：字段、权限、幂等、并发审计、UTC时间及迁移恢复边界。
- [日期汇率与原币换算](development/dated-exchange-rates.md)：精确日期、十进制、不可变快照服务、权限及迁移恢复。
- [产品类型与 SKU 编号基础](development/product-types-and-sku-numbering.md)：类型维护、持久编号、事务衔接、权限及迁移恢复。
- [商品档案、配方映射与参考测算设计](development/product-catalog-design.md)：原SKU与变体独立身份、成品映射、组件复用、报价及空海运参考测算边界。
- [项目整体一致性审查](testing/project-consistency-review.md)：2026-10-13需求、方案、实现及证据核对，已复现风险、修复优先级与回归缺口。
- [产品类型页面验收](testing/product-types-ui.md)：隔离环境的真实页面、权限及编号回归证据。
- [日期汇率页面验收](testing/dated-exchange-rates-ui.md)：手工维护、缺汇率提示、历史和换算预览的实际验证。
- [供应商基础资料页面验收](testing/supplier-management-ui.md)：真实前后端角色、分页、启停、审计故障恢复及并发冲突证据。
- [发布、日志与恢复规范](operations/release-and-recovery.md)：发布流程、日志管理和恢复验证。
- [首个业务闭环验收基准](testing/first-business-loop.md)：固定测试数据、预期结果和必要回归场景。
- [后端测试基线](testing/backend-test-baseline.md)：JUnit入口、实际Java17干净编译/93项回归证据及覆盖边界。
- [工程基线交接](testing/phase-zero-verification.md)：P0-27模块运行入口、已有验证证据及阶段0外部对接/上线未决项。
- [管理员解除临时锁定页面验收](testing/account-unlock-ui.md)：行内解锁、合成 API 浏览器验收及真实联调待验收项。
- [领星交互观察记录](development/lingxing-interaction-reference.md)：本地账号、商品测试样例、采购列表观察与必要业务差异。

## 放置约定

按实际需要增加以下分类，不预建空目录：

| 位置 | 内容 |
|---|---|
| `requirements/` | 业务需求、需求变更和范围说明 |
| `business/` | 业务规则、流程和口径细化 |
| `architecture/` | 架构设计与重要技术决策 |
| `development/` | 开发规范、接口对接与配置说明 |
| `operations/` | 部署、监控、备份和恢复 |
| `testing/` | 测试方案、验收场景和必要的验证记录 |
| `archive/` | 已明确失效但需要保留的历史文档 |

模块启动和构建说明维护在各模块 README；文档附件放在所属文档附近的 `assets/` 中。新增、移动或归档文档时同步更新本索引及相关链接。同一主题优先维护已有文档。

所有材料按内容归类，其他类别以此类推。运行、构建、测试和调试日志放在对应环境的 logs/，本地临时任务日志放在 .local/logs/；日志不放入 doc/，不提交 Git。
