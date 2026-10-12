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

Linux运行 `scripts/test-database-migrations.sh`，Windows PowerShell运行 `./scripts/test-database-migrations.ps1`，自动生成独立临时MySQL/Redis及测试库，覆盖初始化、显式已知基线接管、增量、重复执行、校验和及未来版本、失败阻断、真实mysqldump备份恢复和事务回滚/提交。Boot/Druid/MyBatis集成验证迁移先于业务会话工厂，使用真实Mapper查询；整个profile还运行下述登录和供应商用例，不覆盖尚未实现的库存/财务事务。

安全配置用例验证缺少数据库凭据、弱或非法 Base64 令牌密钥、监控台缺少独立凭据时阻断，异常不输出敏感值；合法密钥实际完成 HS512 签名与解析。实际连接和启动方式见[后端说明](../../erp-front/README.md)。

`LoginBoundaryTest` 追加验证码错误/缺失/过期不进入密码认证、成功验证码消费一次、停用和删除账号先于密码校验拒绝的模拟用例。缺失验证码现返回业务错误，不再空指针异常。真实 Redis TTL、并发验证码消费及完整 HTTP 登录留在 P0-16.b，不以模拟结果代替。

`LoginFlowIT` 启动只绑定 loopback 的完整应用、临时 MySQL/Redis，使用合成账号和随机令牌密钥，通过实际 HTTP 验证验证码、五次锁定、真实 TTL 到期、成功清零、停用拒绝、规范账号名、管理员解锁与只读拒绝，以及并发失败上限。103 条同名账号验证客户端排序后的唯一键顺序、100 条上限和 pageSize=0 默认 10 条；16 个现有分页端点均通过真实查询。最新验证码并发消费和解锁审计验证结果以执行记录为准。

`SupplierFlowIT` 通过真实HTTP/MySQL/Redis覆盖角色权限、输入边界、并发创建幂等、版本冲突、创建/编辑/启停审计失败回滚、UTC日期处理、路由菜单及分页；细节见[供应商接口契约](../development/supplier-management.md)。Failsafe串行运行各测试类且不复用JVM，避免若依静态AsyncManager持有前一应用已关闭的线程池；不是生产线程池改造。

供应商基线3fb07e7已执行48项单元测试及14项集成测试，证据见[工程基线交接](phase-zero-verification.md)；不将历史模拟用例的覆盖限制误写为当前整个profile没有登录/数据库验证。生产及真实店铺边界保持未验证；随后Java17验证见下节。

随后日期汇率增量新增26项`ExchangeRateServiceTest`单元及5项`ExchangeRateFlowIT`真实HTTP/MySQL/Redis集成，覆盖精确日期、BigDecimal舍入、不可变快照、三角色、幂等/并发、审计失败回滚、UTC及菜单升级。2026-10-11最终共74项单元+19项集成全部通过，日志摘要及页面边界见[汇率验收](dated-exchange-rates-ui.md)；实际采购/费用确认持久化仍待P1-18.b。

## Java17实际运行验收

P0-20.j17，2026-10-11：Microsoft OpenJDK17.0.20.1+1-LTS、Windows11 x64、Maven3.6.3、隔离MySQL8.4/Redis7.4；保留系统默认Java21。来源为[Microsoft官方下载页](https://learn.microsoft.com/en-us/java/openjdk/download)，固定包 `https://aka.ms/download-jdk/microsoft-jdk-17.0.20.1-windows-x64.zip`，下载对应 `.sha256sum.txt` 并核对后才运行。包SHA256 `3D9006956FC8AF5601CD24FFC4F468BEF48279C7EBD8171B9BDF90D0AABFBF1F`；保留包内许可证，便携JDK在忽略目录 `.local/tools/microsoft-jdk-17.0.20.1/`，不是应用新增依赖或系统安装。

本次先用同一Java17执行 `mvn.cmd -B -f erp-front/pom.xml clean`，退出0，清除全部7个子模块旧target；再运行 `pwsh -NoProfile -File scripts/test-database-migrations.ps1`，退出0、8模块成功。13份Surefire/Failsafe XML逐项确认 `java.version=17.0.20.1`，合计74项单元、19项集成，无失败/错误/跳过；不仅是Java21编译成17字节码。覆盖真实SDK模型及合成认证、登录/权限、7份迁移/备份恢复、供应商事务和汇率并发/舍入/回滚。

| 本地证据 | SHA256 |
|---|---|
| `.local/logs/P0-20.j17/integration.log` | `3C2538706DF7EB23DF19402BCB7574BF41994C31CD430BF01120EEA65C6808D4` |
| `.local/logs/P0-20.j17/clean.log` | `84A5B73DAA1E5DED9C9F3820FC4ED495E6C78D651BC4F211A35A8DFE1D23B365` |
| `.local/logs/P0-20.j17/runtime.log` | `B61122910CB56CE16C86FACA3C9A5FF85BD8B6EA0040845CE68CD1FB4D161ACF` |
| `.local/logs/P0-20.j17/jdk-bootstrap.log` | `41B6F3B1726A1076002AD61F947E118A21E1C3896CE9F55ADD7AC90C2ECEBAA7` |

运行版本为供应商3fb07e7加当前未提交日期汇率成果；19个变更代码/配置文件清单及摘要在同目录 `tested-source-manifest.json`，回归后核对未变化。生成jar摘要 `4428FCB50C3D87CFFDE9174742AB0DC83A170EEFE27F76CCAA338CD15F201F8E` 仅定位本地本次产物，不作为正式发布版本。报告、源码复核和清理探针见 `evidence-verification.log`。

隔离容器已由finally清理，复查剩余0、8080/5173监听0，进程JAVA_HOME/PATH恢复至默认Java21；删除本轮ZIP及临时材料，保留可复用便携JDK和日志。前端代码未变，沿用汇率最终生产构建及Java21下实际页面验收；未在Java17下再次操作浏览器。真实店铺、正式业务库、生产部署/容量/恢复目标、完整CVE及P1-18.b仍未验证，不将运行兼容通过扩展为生产就绪。
