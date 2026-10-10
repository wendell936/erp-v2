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
