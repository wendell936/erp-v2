# 开发执行记录

本文件按事件追加，任务当前状态见[任务台账](task-board.md)。人工维护的推进摘要属于正式开发文档；原始运行输出按任务 ID 存在环境日志目录，不嵌入本文件、不提交 Git。日期按 Asia/Shanghai；同日事件使用递增序号。修正通过新事件说明，不删除失败记录。

## 2026-10-10 / E001：开始阶段 0 与建立追溯

- 任务：P0-01、P0-05。
- 授权：用户要求按已同意计划推进、细化原子任务并保留可追溯日志；确认云端工作区用于开发测试，Amazon 店铺为美国站。
- 环境：当前本地工作区 `E:\work\erp-v2`，仓库基线提交 `88f8a58`；已有未提交文档改动保留，不创建提交或推送。
- 静态检查：后端声明 Java 17/Spring Boot 4.1.0，当前本地实际 Java 21.0.11、Maven 3.6.3；Node 24.21.0、npm 11.19.0。Java 17 运行时兼容性尚未验证。发现原有 Surefire 2.12.4 及无正式测试目录，后续必须升级测试执行方式并用真实用例验证发现能力，不将 `No tests to run` 当测试通过。
- 配置差距：框架有密码计数/超时能力；配置锁定时间为 10 分钟，需求中的 15 分钟是建议值，不直接视为缺陷；管理员解除临时锁定操作尚需实现核对。分页类未体现业务单次大小上限。均已拆入阶段 0 任务。
- 产物：任务台账、执行记录、根目录日志忽略规则、索引及计划引用。当前任务数不代表工期或业务进度百分比。
- 未决：云端可操作入口/项目路径，实际数据库版本和库状态；真实同步前确认时区及启用边界。独立本地准备继续。

## 2026-10-10 / E002：后端基线首次执行失败并重试

- 任务：P0-02。
- 命令：`mvn.cmd -B -f erp-front/pom.xml verify`。
- 首次结果：退出码 1；沙箱内无法写入当前 Maven 缓存 `E:\local\repo`，依赖解析失败，未进入完整编译和测试。
- 日志：`.local/logs/P0-02/backend-baseline.log`。
- 恢复：已通过工具请求并获得本次构建的权限，执行同一构建，日志 `.local/logs/P0-02/backend-baseline-escalated.log`；本事件记录时尚在运行，最终结果由后续事件补充。

## 2026-10-10 / E003：启动前端原依赖安装

- 任务：P0-03。
- 命令：`npm.cmd --prefix erp-back install --no-audit --no-fund`。
- 日志：`.local/logs/P0-03/frontend-install.log`。
- 结果：本事件记录时仍在运行，尚不能标记安装或前端构建通过。
- 限制：原仓库忽略锁文件，当前未改变锁文件管理策略；构建验证另由 P0-04 记录。

## 2026-10-10 / E004：后端原基线构建完成

- 任务：P0-02；结果：已完成构建基线检查。
- 命令：`mvn.cmd -B -f erp-front/pom.xml verify`，退出码 0，原有 7 个 Reactor 模块均为 SUCCESS。
- 证据：`.local/logs/P0-02/backend-baseline-escalated.log`，产物 `erp-front/ruoyi-admin/target/ruoyi-admin.jar`。
- 实质限制：构建显示 `No tests to run`，没有业务测试通过证据；未启动应用、连接数据库或验证 Java 17 运行时。P0-13/P0-14 独立负责测试工具和集成环境。

## 2026-10-10 / E005：追溯机制完成并开始 ERP 模块接入

- 任务：P0-05 已完成；P0-08 开始。
- 产物：`doc/development/task-board.md`、本执行记录、根 `.gitignore`、`doc/README.md`、开发计划和 AGENTS 的执行要求。
- 检查：`git diff --check` 退出码 0。日志放置与根目录约定一致；原始构建日志被忽略。
- 下一步：新增 `erp-business` 模块及 `ruoyi-admin` 依赖，保持现有 Controller、MyBatis 和组件扫描约定；验证 Maven Reactor 与最终可执行包。

## 2026-10-10 / E006：ERP 模块接入构建通过

- 任务：P0-08 已完成。
- 变更：`erp-front/erp-business/pom.xml`、模块 README、`com.ruoyi.erp` 包说明；父 POM 纳入模块和依赖管理，`ruoyi-admin` 引入业务模块。
- 命令：`mvn.cmd -o -B -f erp-front/pom.xml verify`，退出码 0，8 个模块均为 SUCCESS。
- 产物检查：最终可执行包内存在 `BOOT-INF/lib/erp-business-3.9.2.jar`。包名位于既有 `com.ruoyi` 扫描根下；沿用现有 Mapper XML 配置，未新增实际 Mapper 或业务 Bean，运行时加载留待集成验证。
- 证据：`.local/logs/P0-08/backend-module.log`，SHA256 `03703A085D117AA504070D745D42D697CC4A0856166BAFCB49C24520DFC237D6`。原基线成功日志 SHA256 `A38D99908111AA36D8D5074669DF4010071FF8ECEA5831867437245A008721B5`。
- 未验证：业务数据库、实际页面及 Java 17 运行时；本任务没有创建业务表。

## 2026-10-10 / E007：前端依赖安装重试与测试工具准备

- 任务：P0-03、P0-13。
- P0-03：首次沙箱安装持续无输出，已中止（退出码 1），不能认定成功或认定具体网络故障。获得工具权限后重试同一命令，日志 `.local/logs/P0-03/frontend-install-escalated.log`；npm 缓存日志已出现 registry 成功响应，本事件时仍在安装。
- P0-13：原 Surefire 2.12.4 不足以承载当前 JUnit Platform 测试；固定 Surefire 3.5.6，测试依赖由现有 Spring Boot BOM 管理，测试作用域不进入运行包。先以已有密码校验边界用例验证发现和执行能力，不用空断言冒充测试基线。
- 依据：[Surefire JUnit Platform 文档](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html)、[Spring Boot 测试文档](https://docs.spring.io/spring-boot/reference/testing/index.html)。实际兼容性以随后构建结果为准；依赖许可证及作用域记录在测试说明中。

## 2026-10-10 / E008：前端依赖安装完成

- 任务：P0-03 已完成。
- 结果：安装命令退出码 0，安装 527 个包；未改 `package.json` 的版本声明。
- 证据：`.local/logs/P0-03/frontend-install-escalated.log`，SHA256 `D68A6829DBE12B67C31A2DADDABF4D8C78952D0082E55044AA61F0DD3E7FD856`。
- 风险记录：传递依赖有弃用警告，npm 提示部分安装脚本尚未允许；本任务未开启这些脚本，也未执行依赖安全审计。前端是否可构建由 P0-04 单独验证。

## 2026-10-10 / E009：测试执行基线完成

- 任务：P0-13 已完成。
- 变更：父 POM 固定 Surefire 3.5.6，`ruoyi-framework` 添加测试作用域 starter 和禁止零测试通过配置；新增 `SysPasswordServiceTest`，说明维护于 `doc/testing/backend-test-baseline.md`。
- 初次离线验证：退出码 1，缺少 `spring-boot-starter-test:4.1.0` 缓存，日志 `.local/logs/P0-13/backend-tests-offline.log`，没有进入测试。
- 恢复验证：获得工具权限后 `mvn.cmd -B -f erp-front/pom.xml verify` 退出码 0，8 个模块打包成功；框架报告实际运行 5 项测试，失败 0、错误 0、跳过 0。
- 证据：`.local/logs/P0-13/backend-tests.log`，SHA256 `C74E8E48B7EC408F441D965DA1CDA35734D24D9C25B676C753D10149360289A9`；报告 `erp-front/ruoyi-framework/target/surefire-reports/com.ruoyi.framework.web.service.SysPasswordServiceTest.txt`。
- 限制：模拟 Redis，不代表真实 TTL/并发、验证码、停用、管理员解锁或数据库集成已验收；没有为业务模块虚构空测试。

## 2026-10-10 / E010：前端基线构建权限失败并重试

- 任务：P0-04 开始。
- 命令：`npm.cmd --prefix erp-back run build:prod`。
- 首次结果：退出码 1；Vite 在真实路径解析 `src/main.js` 时收到沙箱 EPERM，0 个模块转换，不能算源码编译通过。
- 证据：`.local/logs/P0-04/frontend-build.log`。
- 恢复：获得工具权限后执行同一命令，日志 `.local/logs/P0-04/frontend-build-escalated.log`，结果后续追加。

## 2026-10-10 / E011：前端生产构建完成

- 任务：P0-04 已完成。
- 命令：`npm.cmd --prefix erp-back run build:prod`，重试退出码 0，Vite 构建完成，产物 `erp-back/dist/`。
- 证据：`.local/logs/P0-04/frontend-build-escalated.log`，SHA256 `0FC7DC485B6E90F1AF733E2B621AD450685ADC2C58ECF88A887C9BD2FFA7996A`。
- 说明：无需开启 npm 提示的未允许安装脚本即可完成当前构建；未修改前端依赖声明。产物打包体积和页面响应仍需后续实际数据/页面验证。
- 未验证：浏览器登录、接口联调和 ERP 页面；没有将构建等同 UI 验收。

## 2026-10-10 / E012：本批收尾及后续入口

- 任务：P0-01/02/03/04/05/08/13 已完成，共 7 项；其余任务保持待开始或待确认，当前无后台运行构建。
- 台账检查：148 个稳定任务 ID，无重复 ID，无未知依赖 ID；检查证据 `.local/logs/P0-05/task-board-check.json`，最终状态统计随后更新。原始日志、依赖及构建产物已核对忽略规则。
- 模块说明：更新后端、前端和 `erp-business` README 的实际构建入口；正式测试说明已挂文档索引。新增模块仍为工程骨架，不包含商品/采购等已实现业务。
- SDK 先行观察：本地缓存官方 `spapi-sdk:1.13.0` 中可见 Orders v2026-01-01、Catalog、Listings、FBA Inventory、Inbound、Finances、Reports、Sellers 类。此为归档静态观察，不是 SDK 依赖树/运行兼容性或店铺权限验证；P0-20 仍待开始，尚未向项目加入 SDK 或固定其版本。
- 后续顺序：P0-20 官方 SDK 兼容性评估；取得云端入口后执行 P0-06/07 核对开发测试环境及真实数据库状态，再做 P0-09～14 中依赖环境的工作。权限矩阵和业务资料按台账对应节点确认，不重新询问已同意的总体安排。
- 当前限制：尚无可调用云端工作区入口或项目路径，不能声称已连接云端。美国站已确认，报表时区及启用日期未确认，不启动真实业务数据同步；生产发布、真实 Amazon 写操作仍不在本批授权范围。

## 2026-10-10 / E013：交付检查结果

- 台账最终统计：148 项，已完成 7、待开始 127、待确认 14；无重复任务 ID、无未知依赖 ID，结果保存于 `.local/logs/P0-05/task-board-check.json`。
- 检查：正式文档相对链接均可解析；最终后端运行包未包含 JUnit、Mockito、Spring Test 或 AssertJ 测试库；`git diff --check` 退出码 0。
- 所有本次构建进程均已结束；没有提交、推送或部署。根目录只新增必要 `.gitignore`，执行日志集中放 `.local/logs/`，需要保留作为失败与恢复证据。

## 2026-10-10 / E014：数据库迁移工具初步建议

- 任务：P0-09 开始静态评估，未完成最终技术决策；P0-10～12 保持待开始。
- 触发：用户询问是否可以引入数据库迁移框架。结论为优先 Flyway，复用 Spring Boot 集成及版本化 SQL，建议与待验证条件写入 `database-migrations.md`，同步开发计划与任务台账。
- 核对：本地 Boot 4.1.0 BOM 管理 Flyway 12.4.0、Liquibase 5.0.3；查阅 Spring Boot、Flyway、Liquibase 官方文档/仓库，区分框架支持、实际数据库兼容和许可证核对。候选版本未写入项目依赖。
- 限制：真实 MySQL 版本、表结构与已有基线尚未核对；未下载或运行迁移工具、未连接数据库、未修改运行配置。依赖树与安全问题检查、最终架构决策及数据库验证留在后续任务。
- 验证：本次仅修改 Markdown；`git diff --check` 退出码 0，另逐行核对本次四份文档的行尾空白和冲突标记，通过（包含尚未跟踪的文档）。引用继续指向已有规范及实际查阅的官方页面；不将静态检查算作迁移通过。

## 2026-10-10 / E015：官方 SDK 兼容性与认证适配开始

- 任务：P0-20、P0-21 开始；迁移工具实际接入仍等待 P0-07 的数据库环境核对。
- 输入与范围：单美国站、自有已授权应用；采用官方 `spapi-sdk:1.13.0` 验证候选，仅装配只读 Sellers 客户端，不在启动、测试或本轮操作中调用真实 Amazon 服务。
- 源码发现：官方 SDK 的 LWA 客户端使用内部全局默认 HTTP 客户端，未公开超时注入；默认缓存是先读后请求，不能合并并发刷新。`LWAAuthorizationCredentials.toString()` 包含敏感字段，异常响应也可能含外部文本，不可直接写日志。
- 实施：保留官方 API、模型和操作限流；通过公开的 signer 扩展点补齐有限超时、内存令牌缓存、刷新互斥与安全错误。缺配置或未启用时不注册对外客户端；凭据由环境提供，不持久化令牌。
- 验收：模拟授权成功、缓存复用、提前刷新、并发、授权/网络/无效响应失败、恢复、来源地址约束；验证真实生成的 Sellers 请求和解析、SDK 本地限流、错误头及依赖冲突。依赖树、构建和运行时结果后续追加，不提前标记通过。

## 2026-10-11 / E016：SDK 验证失败与恢复

- 任务：P0-20、P0-21，仍在验证。
- 依赖分析：默认 `dependency:tree` 离线失败，缺少旧默认插件 2.8；改用明确的 3.8.1 后仍缺其传递缓存。两次退出码均为 1，日志为 `.local/logs/P0-20/dependency-tree-offline.log` 和 `dependency-tree-pinned-offline.log`。获得工具权限后执行固定插件的在线分析，退出码 0，日志 `dependency-tree.log`。
- 首次构建：`mvn.cmd -o -B -f erp-front/pom.xml verify` 停在原密码测试；Surefire dump 显示 JVM attach 命名管道权限错误，已中止（退出码 1），没有记为测试通过。日志 `.local/logs/P0-21/backend-sdk-offline.log`，诊断 `ruoyi-framework/target/surefire-reports/2026-10-10T23-57-44_611-jvmRun1.dumpstream`。
- 恢复构建：获得工具权限后完整 `verify`，原 5 项密码测试通过；业务模块测试编译失败，误用了 SDK 布尔属性 getter。已按实际生成代码改为 `getIsParticipating()`，同时目标接口类名以 SDK 源码核对；该次退出码 1，日志 `.local/logs/P0-21/backend-sdk-verify.log`。修复后重新执行完整验证，结果随后追加。

## 2026-10-11 / E017：SDK 及认证适配验证完成

- 任务：P0-20、P0-21 已完成当前实现及模拟验收；真实授权、角色和连接仍由 P0-22/24 承担。
- 首次恢复：完整 `verify` 退出码 0，8 个模块成功，新增 25 项及原 5 项测试全部通过。日志 `.local/logs/P0-21/backend-sdk-recovery.log`，SHA256 `01F52F317026C61ED73FE924A8C09D211E14963284882D4690238BEF1258A0E3`。
- 包检查发现：SDK 带入的 `swagger-annotations:2.2.16` 与现有 `swagger-annotations-jakarta:2.2.52` 含同名注解类；排除前者并直接复用现有 Jakarta 版本，避免取决于类加载顺序。没有升级 springdoc 或核心框架。
- 最终验证：`mvn.cmd -B -f erp-front/pom.xml verify` 退出码 0；8 个模块成功，30 项测试失败/错误/跳过均为 0。日志 `.local/logs/P0-21/backend-sdk-final.log`，SHA256 `7AC3243C5388095F3363754E7A60C338A239599CBE120FADC5F58FAA5B64594A`。再次检查运行包不存在旧 Swagger 注解包及 JUnit/Mockito/Spring Test/AssertJ。
- 依赖证据：固定插件 3.8.1 的最终离线 `dependency:tree` 退出码 0，日志 `.local/logs/P0-20/dependency-tree-final.log`；版本与许可证、SDK 源码缺口、安全配置、模拟边界记录于 `amazon-sp-api-integration.md` 并挂索引。SDK 归档 SHA256 `0301D25FC7F7EC2BD146943F4C979502FBB879651557BA5AD76A333ACC60030B`。
- 限制：当前实际 JDK 21.0.11，目标 17；未运行 Java 17、完整漏洞扫描、真实 TLS/超时演练、全应用数据库/Redis启动或真实 Amazon 调用。前端未修改，无需重复其既有成功构建。

## 2026-10-11 / E018：按用户要求整理当前提交

- 用户明确要求先提交当前所有需要提交的文件；本轮先收尾验证并提交，云端入口问题保留待确认，不继续数据库变更。
- 范围：此前需求修订、仓库约定、开发计划/台账/执行记录、ERP 模块、测试基线，以及本轮官方 SDK/安全认证适配、配置样例和说明。执行日志、依赖、构建产物被忽略，不纳入提交。
- 提交前检查：任务 ID/依赖、文档相对链接、暂存文件范围、空白/冲突标记及明显凭据格式扫描；结果随实际操作追加。提交哈希在提交完成后追加，不预先填写；本轮仅本地 Git 提交，不推送或部署。
- 实际检查通过：148 个稳定任务 ID，无重复/未知依赖；状态为已完成 9、执行中 1、待开始 124、待确认 14。文档相对链接可解析，暂存 33 个正式文件，`git diff --cached --check` 退出码 0，未发现生成物/日志或扫描规则覆盖的明显凭据格式。该扫描不是完整秘密检测或漏洞审计。
