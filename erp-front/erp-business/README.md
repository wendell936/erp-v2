# ERP 业务模块

任务：P0-08、P0-20、P0-21。业务需求和进度分别见[需求基线](../../doc/requirements/requirements-checklist.md)及[任务台账](../../doc/development/task-board.md)。

本模块承载商品、采购、库存、加工、FBA、经营财务及 Amazon 适配，按业务包随实现增加目录。当前已有 Maven 骨架及官方 SP-API SDK 认证适配；商品、采购等业务尚未实现。

- Java 包：`com.ruoyi.erp`；由 `ruoyi-admin` 启动类所在 `com.ruoyi` 包覆盖组件扫描。
- MyBatis Mapper 放入相应业务 `mapper` 包，XML 使用 `src/main/resources/mapper/erp/` 下的业务目录；沿用应用现有 `classpath*:mapper/**/*Mapper.xml` 配置。实际 Mapper 随业务实现及集成测试验证。
- Controller 放入 `ruoyi-admin` 的 ERP 业务包，本模块不依赖 `ruoyi-admin`，不另建启动应用或服务。
- 依赖 `ruoyi-common` 和官方 SP-API SDK；Amazon 配置默认关闭，启用只装配客户端，不自动调用。凭据配置、认证适配原因、依赖及测试边界见[接入说明](../../doc/development/amazon-sp-api-integration.md)。不提前创建空业务包或表。

## 构建

在 `erp-front` 目录执行：

```text
mvn -B -pl erp-business -am verify
```

完整应用打包：

```text
mvn -B -pl ruoyi-admin -am verify
```

项目声明 Java 17；本次环境工具和验证结果见[执行记录](../../doc/development/execution-journal.md)。运行环境还需实际数据库和 Redis 配置，构建通过不代表应用已启动。日志保存在任务所属环境 `logs/` 或仓库 `.local/logs/P0-08/`，不提交日志或产物。
