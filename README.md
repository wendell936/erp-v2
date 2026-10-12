# Amazon ERP

单公司、单 Amazon 店铺、FBA 场景的 ERP，基于若依的模块化单体。业务范围与实际进度以[文档索引](doc/README.md)、[需求基线](doc/requirements/requirements-checklist.md)和[任务台账](doc/development/task-board.md)为准。

| 目录 | 职责与入口 |
|---|---|
| `erp-front/` | Java/Spring Boot/MyBatis 后端，见[后端运行说明](erp-front/README.md) |
| `erp-back/` | Vue 3/Element Plus 前端，见[前端运行说明](erp-back/README.md) |
| `scripts/` | Windows/Linux 隔离数据库集成测试入口 |
| `doc/` | 需求、业务、开发、运维及验收材料 |

本地构建、真实隔离数据库/HTTP 及页面验证的证据见[工程基线交接](doc/testing/phase-zero-verification.md)。这不代表商品、采购、库存、利润及现金业务闭环已完成，也不代表真实 Amazon 对接或生产发布已验收。

开发遵循 [AGENTS.md](AGENTS.md)，按主题分支推进。凭据通过安全配置注入，原始日志和临时材料仅放 `.local/` 或环境日志目录，不入库。2026年10月继续遵守[不触发 GitHub Actions 的约束](doc/development/cloud-continuation.md)。
