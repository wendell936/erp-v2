# 项目文档索引

## 当前文档

- [项目开发约定](../AGENTS.md)：企业级开发原则、业务边界、质量要求和工作空间管理规则。
- [业务需求基线](requirements/requirements-checklist.md)：Amazon ERP 的业务目标、流程、规则、推进重点及范围外事项。
- [需求与验收规范](requirements/requirements-workflow.md)：需求描述、变更和完成标准。
- [业务术语、编码与单据规范](business/business-conventions.md)：库存术语、编码和单据纠错边界。
- [编码、数据与接口规范](development/engineering-standards.md)：分层、建模、接口、Git 和环境隔离。
- [ERP 分阶段开发计划](development/development-plan.md)：现有框架评估、ERP 模块组织、阶段依赖、SP-API 对接及验收安排。
- [开发任务台账](development/task-board.md)：细任务 ID、依赖、状态、验收条件和待确认项，跟进进度的入口。
- [开发执行记录](development/execution-journal.md)：按任务关联的实际变更、验证、失败与恢复摘要。
- [数据库版本管理规范](development/database-migrations.md)：初始化、增量迁移及工具接入要求。
- [Amazon SP-API 接入与配置](development/amazon-sp-api-integration.md)：官方 SDK、认证适配、安全配置、依赖兼容及验证边界。
- [发布、日志与恢复规范](operations/release-and-recovery.md)：发布流程、日志管理和恢复验证。
- [首个业务闭环验收基准](testing/first-business-loop.md)：固定测试数据、预期结果和必要回归场景。
- [后端测试基线](testing/backend-test-baseline.md)：JUnit 执行入口、测试依赖及当前认证用例的覆盖边界。

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
