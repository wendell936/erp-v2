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

## 2026-10-11 / E019：当前成果已提交

- 已执行用户授权的本地提交：`557a1f21331b6bc8245997a66cbcd2d3010aff13`，标题 `feat: establish ERP baseline and SP-API integration (P0-01 to P0-21)`，33 个文件；标题中任务范围表示关联范围，未完成项继续以任务台账为准。
- 对应成果：工程/追溯基线 P0-01/02/03/04/05/08/13，以及官方 SDK/认证适配 P0-20/21。数据库及真实店铺对接没有因提交自动标记完成。
- 提交后 `git status --short` 无输出；本条为随后追加的哈希追溯记录，单独以文档提交入库。没有推送、部署或真实 Amazon 调用。

## 2026-10-11 / E020：云端接续及分页边界

- 用户授权继续后续任务，并确认本月 GitHub CI 额度用完；本轮全部验证在云端开发环境执行，不触发 Actions，不推送或创建 PR。用户另确认远端 master 为 `b17fc6c0da5b90566855b86ba91e9fbd41761adc`、工作流运行数 0。
- P0-06：云端 `/workspace/erp-v2` 已快进同步到该提交，保留本轮接续文档并解决索引冲突；Docker 28.4.0、Java 21.0.12.1、Node 与 Maven 3.9.11 可用。Maven 临时安装及 settings 位于 `.local/tools/`，不入库。Java 17 实际运行仍未验证。
- P0-07：新建独立 MySQL 8.4.11 空库 `erp_test` 和 Redis 7.4.11，版本查询成功、表数 0、PING 为 PONG。MySQL 无网络、无映射端口或宿主数据卷，仅 docker exec 访问；Redis 无映射端口。未接入真实业务数据或外部数据库。
- 基线验证：`mvn -o -B -Dmaven.repo.local=/workspace/.cache/maven -f erp-front/pom.xml verify` 因缓存来源 ID 不匹配退出 1；调整临时镜像 ID 并沿用会话代理后，`mvn -B -s .local/tools/maven-settings.xml -f erp-front/pom.xml verify` 退出 0，8 模块成功。日志 `.local/logs/P0-06/backend-verify.log`、`backend-verify-recovery.log`。
- P0-18 拆为 P0-18.a 请求分页边界、P0-18.b 列表稳定排序，更新 P1-04 的前置依赖。TableSupport 保留默认每页 10 条、上限 100 条，非正页号归 1、非正条数归 10，参数溢出沿用解析默认值，避免 pageSize=0 关闭分页。新增 3 项请求到 PageHelper 的测试，涵盖 9 组边界输入。
- 首次分页完整验证在默认沙箱失败：新增 3 项测试通过，但原 5 项密码测试因 Mockito 无法动态附加代理报错；未将此标为代码失败或完整验证通过。日志 `.local/logs/P0-06/backend-pagination.log`。按已成功基线的命令权限恢复后重新验证，结果随后追加。
- 最终恢复验证：相同离线 verify 在允许本机代理附加所需网络权限下退出 0，8 模块成功；33 项测试，失败 0、错误 0、跳过 0。日志 `.local/logs/P0-06/backend-pagination-recovery.log`，SHA256 `abf0d6127dbfe0f6b92a7e2d6eb8ca04a4c31492a16afc04064cb11f17fdbaf0`。P0-18.a 已完成；P0-18.b 稳定排序、P0-09 迁移工具兼容与恢复验证仍待推进。

## 2026-10-11 / E021：迁移接入、隔离测试与环境配置

- 用户授权按台账自动推进，仅在需确认/协助时询问；本轮不使用 GitHub CI，不提交、推送或部署。P0-09～12、P0-14 开始实施。
- 在入口模块接入 BOM 管理的 Flyway 12.4.0，静态核对 parent POM 的 Apache-2.0；未运行完整漏洞扫描。唯一迁移资源目录打入后端包，原若依/Quartz SQL 移动为两个基线，移除初始化 DROP。默认关闭，显式启用后固定物理主库，拒绝自动 baseline/clean/乱序，验证名称、校验值和未来版本；MyBatis 依赖数据库初始化。
- 首次候选依赖构建通过：`mvn -B -s .local/tools/maven-settings.xml -f erp-front/pom.xml verify`，退出 0，日志 `logs/P0-09/flyway-build.log`。
- 独立入口 `scripts/test-database-migrations.sh` 创建随机凭据、loopback 临时 MySQL 与随机库，退出清理容器。最初重定向到尚未创建的日志目录退出 1，未创建容器；补目录恢复。
- 失败/恢复：Flyway 空参数的重载歧义导致编译失败，明确传 `new String[0]`；日志 `logs/P0-12/migration-it.log`。Failsafe 使用 Boot 重打包 JAR 导致类/资源缺失，指定编译输出目录；日志 `migration-it-recovery.log`。Druid/MyBatis 集成用例的别名未包含公共实体，改用应用原 `com.ruoyi.**.domain` 配置；日志 `migration-it-classpath.log`。
- 首轮真实迁移验收：日志 `logs/P0-12/migration-it-final.log`，退出 0，8 模块成功，6 项 MySQL 集成与新增 2 项配置测试通过。真实 mysqldump 备份恢复保留合成记录，失败 DDL 保留部分表及失败历史，后续迁移与重试阻断；恢复备份后重新验证并执行修正后的未成功迁移。没有自动 repair 或改成功脚本。
- 测试发现硬编码日志目录 `/home/ruoyi/logs` 无写权限：改为 `ERP_LOG_PATH`，默认 `logs/erp-front`。继续 P0-19：主库连接与令牌密钥由环境注入，移除演示凭据；Druid 控制台默认关闭，启用需独立凭据；增加不自动加载的环境样例。启动前只报告配置名，不输出密码；HS512 密钥检查 Base64 与至少 64 字节长度。Redis/上传/日志均可环境配置。缺失配置现会阻断启动，已更新后端说明。
- 增加真实 MySQL 事务回滚与提交用例作为 P0-14 入口；后续具体业务事务仍按业务任务验证，不提前标为库存/财务验收。最终含安全配置与事务用例的回归结果随后追加。
- 最终验证：通过 `scripts/test-database-migrations.sh`（指定临时 Maven/settings），退出 0，8 模块成功，46 项测试（39 单元/组件 + 7 MySQL 集成），失败/错误/跳过均为 0。日志 `logs/P0-12/migration-and-config.log`，SHA256 `ce981739da1efaa65e74e9d1fa0ddb8962d60aff75e0f4f88bd3224472a45bec`。运行包含两个 SQL、Flyway core/mysql 12.4.0，不含测试库；SQL 无初始化 DROP。本轮测试容器已由退出钩子删除。P0-09/10/11/12/14/19 已完成当前工程验收；真实既有库、Java 17、完整应用登录及部署仍未验收。

## 2026-10-11 / E022：登录组件边界与待确认权限

- P0-16 拆为 P0-16.a 组件边界、P0-16.b 真实 Redis/HTTP 验收；更新 P0-17 前置。原 5 项密码边界继续复用，新增 5 项验证码与停用/删除账号用例。发现 `validateCaptcha` 对缺失 code 调用 equalsIgnoreCase 会空指针，改为验证码业务错误；错误/缺失/过期均不会进入密码认证。
- 初次 verify 因新增用例漏验证一次 Redis 读取导致 `verifyNoMoreInteractions` 失败，补齐交互断言后恢复；不是业务实现失败。日志 `logs/P0-16/login-boundaries.log`、`login-boundaries-recovery.log`，结果随后追加。真实 Redis TTL、HTTP 登录及并发计数仍未验收。
- P0-15 仍待用户确认业务数据范围及成本/利润/资金访问边界，已通过问题工具询问；未自行授予业务权限。
- 登录组件最终 verify 退出 0，8 模块成功、44 项单元/组件测试无失败/错误/跳过；日志 `logs/P0-16/login-boundaries-recovery.log`，SHA256 `b0f4ba637d3e4c4268e4498f7cde4363e5329dad6efa6b7f1834fea2f98139bb`。此前 7 项 MySQL 集成测试已独立通过，本次未修改迁移实现或重跑数据库用例。P0-16.a 完成。

## 2026-10-11 / E023：权限范围已确认

- 用户确认操作、只读人员均可查看全公司业务数据，操作人员可办理业务，只读人员不可修改；成本、利润、资金不另设读取限制。已建立 `doc/business/access-control-matrix.md` 并挂索引，P0-15 完成矩阵确认。
- 具体账号、角色、菜单和业务接口尚未因此自动授权；后续仍逐项实现、验证。P0-17 管理解锁依赖真实登录集成，P0-16.b 保留待开始。

## 2026-10-11 / E024：真实登录、管理员解锁与稳定分页

- P0-16.b 使用完整应用及临时 MySQL/Redis 验证 HTTP 验证码、五次锁定、真实 Redis TTL 到期、成功清零和停用拒绝，日志 `logs/P0-16/login-http-redis-password.log`，退出 0。此前 JUnit instance TempDir 在 BeforeAll 尚未注入改为 static，日志 `login-http-redis.log`；合成测试密码超出原 20 字符上限，改为符合原规则，日志 `login-http-redis-recovery.log`。未为测试放宽密码规则；TTL 用测试配置 1 分钟并显式缩短缓存等待，应用默认 10 分钟不变。
- P0-17.a 开始：通过用户确认的矩阵，注册 `system:user:unlock` 增量权限，仅给有效管理员角色；接口检查后端权限、目标数据范围与存在/删除状态，清除指定密码失败缓存，不改密码或状态，沿用审计注解。前端 P0-17.b 需按工程规范先查看领星交互，已请求参考链接/说明及安全账号配置，尚未实施。
- 代码核对发现读后覆盖的密码失败计数会丢更新、使用用户输入账号名可形成大小写不同锁定键；改用数据库账号名，Lua 原子递增并到上限不续期，成功请求原子检查未锁定后清零，不能清除并发锁定。真实 8 并发失败计数达到且不超过 5，只读 HTTP 解锁被拒绝，管理员解锁及再次解锁成功，停用账号不会自动启用。
- 首次 Lua 测试失败：原 FastJson 序列化 Long 的类名后缀不能作为 Lua 数值参数；为脚本明确采用字符串参数序列化，未更换业务缓存格式，恢复后通过。日志 `logs/P0-17/unlock-and-concurrency.log`、`unlock-and-concurrency-serializer.log`。
- 验证码读后删也有并发重用窗口，改为 Spring Data Redis 的原子 getAndDelete（Redis 6.2+），保留错误验证码消费与缺失业务错误规则；当前实际 Redis 7.4.11。
- P0-18.b 逐项核对系统、监控、调度、生成器共 16 个分页查询的表别名、唯一键及原默认排序。增加 PageUtils/BaseController 受控稳定排序入口，客户端排序后仍追加服务器唯一键，保留角色顺序、字典顺序、通知/日志倒序等原业务排序；没有原排序的列表明确按主键排序。新增排序及注入拒绝用例。
- 通过临时库 103 条同名账号和实际 HTTP 验证跨页按唯一键顺序、pageSize=1000 仅取 100、pageSize=0 返回 10；16 个分页端点均查询成功。`logs/P0-18/stable-pagination.log` 退出 0，8 模块成功。并发增删下 offset 分页不承诺快照一致性。最终并发验证码消费及解锁审计回归随后追加。
- P1-01 的真实首套商品/组件/单位/SKU 已询问用户，尚待资料；不自行发明 SKU 词典或单位换算。

- 最终回归：`scripts/test-database-migrations.sh`（指定本地 Maven 与 settings）退出 0，8 模块 BUILD SUCCESS；48 项单元测试、8 项真实数据库/HTTP 集成测试，共 56 项，失败、错误、跳过均为 0。包含并发验证码仅消费一次、只读解锁拒绝、真实解锁审计、停用账号解锁后仍拒绝登录、16 个分页端点及迁移恢复。日志 `logs/P0-17/final-stage-zero.log`，SHA256 `a13476a8ed44adf9568d3325272f953f674bb120d04549102fa957025d5cb147`。P0-17.a、P0-18.b 完成；P0-17.b 页面尚未实施，不代表阶段 0 全部完成。未提交、推送或触发 Actions。
- 用户补充 P1-01：成品哥特风生日礼品袋，组件贺卡、雪梨纸、纸袋、丝带；用量、单位及 SKU 待补充。用户提供领星登录入口并说明内置浏览器已登录，正在核对本会话浏览器访问能力。
- 浏览器访问核对：当前工具仅可打开 Codex 内置标签，不能读取该标签 DOM 或会话；远程浏览器 Default 档案无登录站点记录。未尝试复用或导出 Cookie，未进入未授权的其他账户。已请求交互说明或远程安全登录方式；P0-17.b、P0-26 继续待参考资料。

## 2026-10-11 / E025：首套组件用量与参考登录接续

- P1-01：用户确认每套雪梨纸 3 张、贺卡 1 张、纸袋 1 个、丝带 2 根；更新业务约定，保留原单位，不推断丝带长度。已有 SKU 及编码/采购换算尚未提供，任务继续待确认。
- P0-17.b/P0-26：用户选择在远程浏览器另行登录，已调用现有 Default 档案的安全登录入口；登录状态由用户在入口保存，不将密码或 Cookie 写入仓库。此次仅业务文档更新，不重复执行无关代码测试，不触发 Actions。
- 用户反馈远程登录卡顿，要求尝试直接使用浏览器。已重新核对当前工具与云端进程：无可读取/点击桌面内置浏览器的接口，无本地浏览器进程；仅能调用打开内置标签工具。已尝试打开领星入口，不把打开标签视为已查看参考页面。已向用户解释 SKU 为内部商品唯一编号，尚未确认是否已有编码。

## 2026-10-11 / E026：授权提交与本地交接

- 用户明确授权提交并推送当前修改，准备切换本地；明确商品编码规则稍晚再定，已更新 P1-01 和业务约定，不重复催问。
- 实现提交 `3a647a33db7d2956b28ac0b949ad074dbe159bbe`：`feat: complete ERP migration and account safety baseline [skip ci]`，共 49 个文件，包含 P0-06/07/09～19 本轮成果、测试入口、权限矩阵、真实首套组件数量及交接说明；P0-17.b 前端与 P0-26 参考观察保持未完成。原始日志、构建产物、临时 Maven/settings、凭据和浏览器会话均未入库。
- 提交前核对：56 项测试最近结果无失败/错误/跳过；仅文档后续更新，不重复无关构建。已运行 diff whitespace、脚本语法、暂存文件/常见密钥模式和本地文档链接核对，均通过。日志摘要沿用 E024 的路径与 SHA256。
- 只读 fetch 确认 origin/master 仍为 `b17fc6c0da5b90566855b86ba91e9fbd41761adc`，仓库无工作流文件、本轮未添加工作流。CLI GitHub API 请求被网络代理拒绝，改用已连接 GitHub 的只读 runs API 成功：推送前仓库工作流运行总数为 0，未采用仅查 PR 事件的包装接口。
- 交接文档提交也加入 `[skip ci]`；接下来按用户授权将两项提交快进推送 `HEAD:master`，不强推、不创建 PR。推送结果及最终交接提交哈希在聊天交付中报告；本地从 origin/master 接续，先核对本地未提交改动。

## 2026-10-11 / E027：本地接续与解锁页面开始

- 按用户要求切回本地 E:\work\erp-v2，开始时 git status --short 无输出。沿用不触发 GitHub CI、不自动提交推送的约束。
- P0-17.b：通过本地内置浏览器成功读取已登录的领星用户管理页，观察到紧凑筛选、角色/状态筛选、表格右侧行内编辑、总数与分页。仅有超级管理员一条记录；未观察到临时解锁入口，不将本项目业务规则归因于领星。
- 页面随后出现安全验证，已请用户手动完成，未自动处理验证码、修改参考账号权限或凭据。P0-26 有数据的商品/采购流程待继续观察。
- 本地实现用户列表行内“解除锁定”，复用 system:user:unlock 权限与 PUT /system/user/unlock/{userId}；确认提示说明只清密码失败计数，停用状态保持。确认及请求期间阻止同一行重复操作，失败保留列表并恢复重试入口。管理员账号也保留解锁入口，接口未禁止该目标；不复用修改/重置密码权限。
- 接下来执行生产构建和页面验证，未运行结果不提前标通过。
- 本地前端构建恢复退出 0，日志 `.local/logs/P0-17/frontend-build-recovery.log`，SHA256 `1D2C29F7A012DE4BF4EF55BC820D097226AD1A7E2478901BC9ECD354D9CC6090`。首次沙箱 realpath EPERM 退出 1，没有记为代码失败或构建通过。
- 实际 Vue 页面配合仅本机合成 API 验证管理员、取消、处理中禁用、停用提示、服务失败后重试和只读入口隐藏；只读解锁按钮数 0，取消没有产生 PUT。模拟 API 首次跨沙箱连接超时，以同权限重启恢复；只读用户名的 link 角色定位失败后按页面与按钮计数核对。后端无修改，不重复独立的既有数据库测试。完整验收及限制见 `doc/testing/account-unlock-ui.md`，P0-17.b 保留待验收。
- P0-26 开始：创建测试商品 `ERP-TEST-LOCAL-20261011-A`，名称 `ERP-TEST-本地交互验证配件`，从空态验证到一条记录、行内操作、编辑单位为件及详情回显。保留该交互样例，未改用户已有对象；没有创建采购/付款/发运。采购当前日期筛选截止 10-07，不以过滤后空态推断全库无数据。范围与差异见 `doc/development/lingxing-interaction-reference.md`，P0-26 仍执行中。
- 本地 Docker server 查询报 Linux engine 管道不存在；尝试启动已安装 Docker Desktop 并核对 WSL，WSL 在当前沙箱返回 E_ACCESSDENIED；未更改系统设置或安装发行版，不据此断言本机未安装 WSL。本次真实后端页面联调未运行。

## 2026-10-11 / E028：Windows Docker 本地测试接续

- 用户确认 Docker Desktop 已安装并授权继续后续任务。普通沙箱访问 Docker 管道被拒绝，正常本地权限只读查询成功：引擎 29.8.2、Linux context；本机初次镜像列表为空。Java 21.0.11，Maven 3.6.3，WSL 有 Ubuntu 20.04/22.04 与 docker-desktop；不改系统设置。
- P0-14.w 开始：现有 Bash runner 和备份恢复用例写死 Unix socket，新增 PowerShell 隔离入口并允许测试用例显式选择固定本机 named pipe，仍拒绝任意远程 daemon。随机凭据仅经进程环境传递，临时容器仅映射 loopback，不挂载宿主数据；finally 清理本次 GUID 容器并恢复环境。
- P0-17.b 真实前后端联调、P0-26 商品/采购交互观察继续推进；当前工作区保留上一轮未提交成果。不推送、不创建 PR、不触发 GitHub Actions。
- Windows 就绪探针前两次失败，日志分别为 `.local/logs/P0-14.w/windows-integration.log`、`windows-integration-recovery.log`；数据库已启动但探针未识别，尚未运行 Maven，不记为测试失败/通过。改用容器内 MYSQL_ROOT_PASSWORD 经 MYSQL_PWD 执行 SQL 探针后恢复，未延长等待冒充修复。
- P0-14.w 完成：Windows Docker Desktop 29.8.2、MySQL 8.4.11、Redis 7.4、Java 21.0.11 本地 `-Pmigration-it verify` 成功，48 项单元测试及 8 项集成测试全部通过，失败/错误/跳过均 0；含实际备份恢复及真实 MySQL/Redis/HTTP 登录和权限链路。日志 `.local/logs/P0-14.w/windows-integration-recovery-2.log`，SHA256 `38D8C7AFE846BC5AC44F3495447E868451E40C024DEF8364679DE4BA9ECFAAB6`。本轮测试容器由 finally 清理。仍未实际验证 Java 17 运行。
- P0-26：新增明确标记的测试供应商及采购草稿 `ERP-TEST-LOCAL-20261011-PO`，数量 2、CNY 单价 5 的合成样例。保存后列表/详情均为待提交、总额10、付款未申请；仅查看行内菜单、步骤与关联单据空态，未提交、下单、付款或入库，观察差异同步参考文档。
- P0-17.b 开始独立临时 MySQL/Redis 与真实 jar/Vue 页面联调。首次临时签名密钥仅48字节，被现有64字节安全校验拒绝；保留失败日志并改为64字节随机值重启，未弱化校验。仅临时验收库使用合成账号及关闭验证码，业务环境无改动。
- P0-17.b 完成真实页面联调：Redis计数5阻止正确密码登录，取消不清计数且无审计；确认后键不存在、原密码登录成功。只读角色真实列表中解锁按钮为0。停用且锁定场景解锁后 status=1 不变，仍拒绝登录；数据库两条解锁审计分别为 oper_id 100/101、admin、/system/user/unlock/2、status=0。证据 `.local/logs/P0-17/real-ui/database-evidence.log` SHA256 `0CBA002CF77244646E7828C8EF6BD8EF8CE33F6E457044FC5018139630648EC5`，截图与场景见验收文档。真实故障注入未重做，失败/重试保持前轮合成API证据。
- P0-26 完成有数据交互观察：稳定的新建空表单必填提示、组合明细表单及图片上传说明均补录；已有SKU尝试后表单保留且列表仍一条，但未捕获返回文案，不记为完整重复规则验收。未保存组合、上传图片、验证已有来源单据跳转，明确作为后续模块参考边界；保留一条测试商品、一条供应商和一张待提交草稿。
- 清理：关闭两个本机验收浏览器页，按已核对的 PID/命令停止本轮 jar 与 Vite，启动脚本 finally 删除本次两个UI容器；复核本轮UI/集成测试前缀无剩余容器，8080/5173无监听。保留原始日志与截图用于复核；临时工具在已忽略的.local，不提交凭据或运行材料。领星参考页留作后续使用。
