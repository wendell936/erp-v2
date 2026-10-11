# 后端测试基线

任务：P0-13。执行结果以[开发执行记录](../development/execution-journal.md)为准；本文描述测试入口及边界，不代表所有业务已验收。

## 工具与依赖

父 POM 固定 Maven Surefire 3.5.6，用于执行 JUnit Platform 测试，替代原默认 2.12.4。版本固定且使用现有 Maven/Java 环境验证，不改变运行框架。`ruoyi-framework`、`erp-business` 引入测试作用域 `spring-boot-starter-test`，版本由现有 Spring Boot 4.1.0 BOM 管理（当前 JUnit Jupiter 6.0.3、Mockito 5.23.0），避免自行拼接不兼容版本。这两个模块设置 `failIfNoTests=true`，防止已有测试意外未发现仍报成功；尚无测试的其他模块不以此冒充测试覆盖。

新增工具用于验证需求中的认证边界，收益为可重复运行、明确失败及测试报告。Surefire/Spring Boot/Mockito 使用 Apache-2.0，JUnit 使用 EPL-2.0；均为常用开源测试组件，测试作用域不进入业务运行包。实际传递依赖和未来升级仍需兼容性核对。依据：[Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html)、[Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)。

## 执行

从仓库根目录执行：

```text
mvn -B -f erp-front/pom.xml -pl ruoyi-framework -am test
mvn -B -f erp-front/pom.xml verify
```

本地原始日志放 `.local/logs/P0-13/`，Surefire 报告位于模块 `target/surefire-reports/`。云端工作区使用该环境 `logs/P0-13/`。目录均不提交 Git，正式记录写命令、退出码、执行数量和失败/跳过数量。

## 当前用例边界

`SysPasswordServiceTest` 使用真实 BCrypt 密码核对和模拟 Redis 缓存，验证首次密码失败、第五次达到阈值、锁定中即使正确密码也拒绝、锁定前成功清零，以及失效窗口后可正常校验。10 分钟来自当前配置，测试不将需求建议的 15 分钟强行固化。

这些用例证明测试工具可执行并覆盖密码服务部分边界。模拟 Redis 失效不能证明真实 TTL、Redis 并发计数或重启行为；验证码、停用账号、管理员解锁和浏览器完整认证流程仍由 P0-16/P0-17 验证。数据库事务、FIFO、金额守恒等随业务模块建立独立用例，不以该基线代替。

`erp-business` 新增的 SP-API 配置、认证及 SDK 模拟测试见[接入说明](../development/amazon-sp-api-integration.md)。测试 HTTP 拦截器替代全部网络，不读取真实凭据。当前本地沙箱出现 Mockito JVM attach 命名管道权限失败时应保留失败证据，改在支持的完整本地或云端测试环境验证；不能跳过测试冒充通过。

## 数据库集成测试

`ruoyi-admin` 引入同 BOM 的测试 starter，Surefire 执行迁移及环境安全配置单元测试；Failsafe 3.5.6 在显式 `migration-it` profile 执行 `DatabaseMigrationIT`。对 Boot 入口模块明确使用 `target/classes`，避免可执行包的嵌套路径影响测试。

运行 `scripts/test-database-migrations.sh` 自动生成独立临时 MySQL 与测试库，覆盖初始化、显式已知基线接管、增量、重复执行、校验和及未来版本、失败阻断、真实 mysqldump 备份恢复和事务回滚/提交。Boot/Druid/MyBatis 集成验证迁移先于业务会话工厂，使用真实 Mapper 查询。测试不覆盖完整登录链路或业务库存/财务事务，后续按对应任务补齐。

安全配置用例验证缺少数据库凭据、弱或非法 Base64 令牌密钥、监控台缺少独立凭据时阻断，异常不输出敏感值；合法密钥实际完成 HS512 签名与解析。实际连接和启动方式见[后端说明](../../erp-front/README.md)。

`LoginBoundaryTest` 追加验证码错误/缺失/过期不进入密码认证、成功验证码消费一次、停用和删除账号先于密码校验拒绝的模拟用例。缺失验证码现返回业务错误，不再空指针异常。真实 Redis TTL、并发验证码消费及完整 HTTP 登录留在 P0-16.b，不以模拟结果代替。

`LoginFlowIT` 启动只绑定 loopback 的完整应用、临时 MySQL/Redis，使用合成账号和随机令牌密钥，通过实际 HTTP 验证验证码、五次锁定、真实 TTL 到期、成功清零、停用拒绝、规范账号名、管理员解锁与只读拒绝，以及并发失败上限。103 条同名账号验证客户端排序后的唯一键顺序、100 条上限和 pageSize=0 默认 10 条；16 个现有分页端点均通过真实查询。最新验证码并发消费和解锁审计验证结果以执行记录为准。
