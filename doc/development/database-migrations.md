# 数据库版本管理规范

## 当前基线

唯一目录为 `erp-front/ruoyi-admin/src/main/resources/db/migration/`。原若依、Quartz 初始化 SQL 已整理为 V2026041701、V2026041702，移除初始化删表语句，按版本打入后端包。

初始化脚本用于新环境基础搭建，不作为持续升级脚本。已有基础脚本不因新增业务被反复覆盖。

## 选型背景（2026-10-10，2026-10-11 已接入）

优先采用 Flyway 管理版本化 SQL。当前项目使用 MyBatis 和单个 MySQL 数据库，已有 SQL 初始化资料；直接评审增量 SQL 符合现有开发方式，无需额外引入结构化变更描述层。Flyway 的版本顺序、执行历史和校验值可用于追溯各环境升级及发现已执行脚本被修改，实际行为仍需集成测试验证。

- Spring Boot 官方支持 Flyway 和 Liquibase。当前项目 Boot 4.1.0 BOM 管理 Flyway 12.4.0、Liquibase 5.0.3；先以 BOM 管理版本作为验证候选，不为迁移工具升级核心框架，也不把 BOM 收录等同实际数据库兼容验证。
- Flyway 接入候选为 `spring-boot-starter-flyway` 和 `org.flywaydb:flyway-mysql`，由应用入口模块装配；核对现有 Druid 数据源、脚本打包路径及初始化时序，不能只加依赖即认定完成。正式版本还需检查依赖树、已知安全问题和所用制品许可证。
- Flyway 开源项目说明采用 Apache-2.0，商业发行版及额外能力须分别核对；当前只计划使用版本迁移及校验能力。Liquibase 可作为备选，支持 XML/YAML/JSON/SQL 变更日志；其当前 Community 仓库标明 FSL，不能沿用旧版本 Apache-2.0 的印象判断许可。当前需求没有必须改选 Liquibase 的收益。
- 接入时保持唯一迁移来源，关闭未知既有库自动 baseline 和 clean，启用脚本校验；上述保护已加入运行配置并通过隔离库验证。开发测试可在启动时迁移；正式环境的执行入口、迁移账号权限与部署阻断策略在技术决策中明确，不随默认启动配置直接沿用。
- 工具不能替代 MySQL DDL 的失败恢复设计；采用新的前向修复脚本及必要备份恢复，不承诺任意变更都能自动回滚，也不在失败后自动 repair 或删除执行历史。

当前采用 Flyway 12.4.0，装配、许可、基线预检和恢复见[技术决策](../architecture/database-migration-decision.md)。迁移默认关闭，显式启用时禁止自动接管未知既有库及自动清库。验收结果以台账和执行记录为准。

依据：[Spring Boot 数据库初始化与迁移集成](https://docs.spring.io/spring-boot/how-to/data-initialization.html)、[Flyway 版本化迁移](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations)、[Flyway MySQL 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)、[Flyway 项目及许可证说明](https://github.com/flyway/flyway)、[Liquibase 项目及许可证说明](https://github.com/liquibase/liquibase)。版本候选另以本地 Boot 4.1.0 BOM 实际内容核对。

## 统一迁移规则

- 新增增量 SQL 统一放在 `erp-front/ruoyi-admin/src/main/resources/db/migration/`，使用 `VYYYYMMDDNN__description.sql`，同日顺序号不重复。正式环境执行过的脚本不可改写，通过新脚本修正。
- 一个迁移明确版本、目的、适用前置版本、执行顺序、数据影响和验证方式。SQL 注释说明不可逆行为及必要前提。
- 数据修正脚本限定对象和条件，先核对预计影响行数，避免无条件更新或删除；必要时提供预检 SQL。
- 不假设 DDL 可以通过事务完整回滚。高风险变更先备份，设计前向修复或恢复方案；删列等破坏性变更分阶段实施。
- 应用与迁移版本共同发布，新版本启动或部署前检查数据库版本。重复执行不得悄悄忽略失败。
- 手工执行仅作为工具接入前的过渡方式：在环境外部的迁移执行台账记录环境、脚本版本、校验值、人员、时间和结果；实际执行日志存入该环境 `logs/`，不写回 SQL 或提交运行日志。
- 不在应用运行时自行建表，不复制一套自研迁移框架，不通过个人数据库客户端修改后遗漏脚本。

## 工具接入要求

在首个业务数据库变更正式落地前，核对实际数据库类型和版本、现有数据状态、Spring Boot 兼容性、许可证及部署方式，完成成熟迁移工具的技术决策并接入。

技术决策放在 `doc/architecture/`，说明选择依据、既有数据库如何建立基线、脚本实际加载路径、执行责任、并发保护和失败恢复。需要工具标准资源目录时迁移脚本并同步索引，保持一个权威来源，不复制维护。

验证至少包括空库初始化、已有基线升级、无变更再次执行、脚本失败后的阻断与恢复，以及应用和数据版本不匹配的处理。不得在未评估已有表结构时直接对现有数据库启用自动 baseline。
