## ERP 开发验证与环境配置

从仓库根目录运行 `mvn -B -f erp-front/pom.xml verify`。数据库集成测试在 Linux 运行 `scripts/test-database-migrations.sh`（需要 Python 3），Windows PowerShell 运行 `./scripts/test-database-migrations.ps1`（Docker Desktop Linux engine）；两者均需要本机 Docker、Maven 和依赖获取条件，可用 `ERP_MAVEN_BIN`、`ERP_MAVEN_SETTINGS` 指定工具和 settings。脚本自动生成临时凭据、创建 loopback MySQL 与 Redis，测试后删除本轮容器；无需真实业务库。日志按任务放 `logs/`，本地临时验收放 `.local/logs/<任务ID>/`，均不提交。普通 verify 不执行需要 Docker 的 `*IT`；显式 `-Pmigration-it` 缺少测试连接配置时失败，不静默跳过。

应用启动前必须安全注入 `ERP_DB_URL`、`ERP_DB_USERNAME`、`ERP_DB_PASSWORD`、`ERP_TOKEN_SECRET`。令牌密钥需为至少 64 字节随机数据的 Base64 编码，可在安全配置中生成，禁止入库或输出日志；更换密钥会使现有登录令牌失效。移除原演示数据库密码和令牌密钥后，缺失配置会在创建服务前阻断启动，不能沿用此前无配置的演示启动方式。

可选环境变量：`ERP_REDIS_HOST`、`ERP_REDIS_PORT`、`ERP_REDIS_PASSWORD`、`ERP_UPLOAD_PATH`、`ERP_LOG_PATH`。日志默认 `logs/erp-front`，上传默认 `.local/uploads`。Druid 控制台默认关闭；启用 `ERP_DRUID_MONITOR_ENABLED=true` 时必须同时注入独立的 `ERP_DRUID_MONITOR_USERNAME`、`ERP_DRUID_MONITOR_PASSWORD`，并按环境限制访问来源。

迁移默认关闭；隔离开发空库可显式设置 `ERP_DB_MIGRATION_ENABLED=true`。未知非空库拒绝自动接管，已有库需备份、结构核对和显式 baseline。脚本目录、失败恢复和部署边界见[数据库迁移决策](../doc/architecture/database-migration-decision.md)。正式部署仍需另外授权，不把临时测试配置用于生产。

[本地配置样例](ruoyi-admin/src/main/resources/application-local.example.yml)不会自动加载；如需文件覆盖，将副本放 `.local/config/application-local.yml`，通过 `--spring.config.additional-location=file:.local/config/application-local.yml` 指定。实际值通过环境或安全配置注入，不写进样例。

### Windows 本地运行

本机默认已按用户要求切为Microsoft OpenJDK17.0.20.1/Maven3.6.3，JAVA_HOME为 `D:\tools\.jdks\ms-17.0.20.1`，系统PATH原Java21入口已替换，Java21保留供恢复；已打开的终端、IDE和Codex需重启以继承新环境。Java、javac及Maven的新子进程版本均已核对为17.0.20.1。POM目标17，已用同一JDK干净编译并通过全部93项隔离回归，来源、摘要及运行边界见[后端测试基线](../doc/testing/backend-test-baseline.md#java17实际运行验收)，原工程证据见[工程基线交接](../doc/testing/phase-zero-verification.md)。

临时选择其他JDK或在新环境指定工具时，可只设置当前进程环境，并将该JDK的bin放在PATH首位；先核对 `mvn.cmd -version` 的实际Java版本。工具不随Git同步，新环境从官方取得并校验后使用自己的路径，例如：

```powershell
$erpPreviousJavaHome = $env:JAVA_HOME
$erpPreviousPath = $env:PATH
try {
    $env:JAVA_HOME = 'D:\tools\.jdks\ms-17.0.20.1'
    $env:PATH = "$env:JAVA_HOME\bin;$erpPreviousPath"
    mvn.cmd -version
    # 在此执行下述构建、运行或隔离回归；核对实际退出码。
} finally {
    $env:JAVA_HOME = $erpPreviousJavaHome
    $env:PATH = $erpPreviousPath
}
```

从仓库根目录执行（先在受保护的进程环境中注入上述数据库、Redis及令牌配置）：

```powershell
mvn.cmd -B -f erp-front/pom.xml verify
# 构建退出0后启动，默认8080；仅绑定本机用于开发。
java -jar erp-front/ruoyi-admin/target/ruoyi-admin.jar --server.address=127.0.0.1
```

集成回归需启动 Docker Desktop 的 Linux engine，再执行 `./scripts/test-database-migrations.ps1`。该入口自建随机隔离库、Redis及合成账号，退出后删除本轮容器；不能将它用作持久业务环境。原始输出重定向到已创建的 `.local/logs/<任务ID>/` 并核对实际退出码。测试不需要真实 SP-API 凭据，默认关闭外部调用。

运行库须事先完成经过授权的迁移，当前共7个版本脚本；普通启动默认不迁移。样例文件显式关闭 Flyway，加载它后不会因环境变量启用迁移。未知非空库按迁移决策预检，禁止直接重置或自动baseline。首次新环境须更换基线演示账号密码并落实角色授权；隔离测试合成账号不是正式账号方案。

启动成功后以前端真实登录及所需业务接口核对连接和权限，不能用进程存在或构建成功代替联调；当前尚无已验收的生产健康检查/告警体系。停止正在运行的jar后再执行重新打包，避免Windows文件占用。当前JSON日期显示仍沿用框架GMT+8配置，不代表店铺/财务业务时区已经确认。

<p align="center">
	<img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">RuoYi v3.9.2</h1>
<h4 align="center">基于SpringBoot+Vue前后端分离的Java快速开发框架</h4>
<p align="center">
	<a href="https://gitee.com/y_project/RuoYi-Vue/stargazers"><img src="https://gitee.com/y_project/RuoYi-Vue/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue"><img src="https://img.shields.io/badge/RuoYi-v3.9.2-brightgreen.svg"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

## 平台简介

本项目的 ERP 业务模块见 [erp-business](erp-business/README.md)，实际开发进度见[任务台账](../doc/development/task-board.md)。仓库根目录可执行 `mvn -B -f erp-front/pom.xml verify` 构建并运行已接入的测试；测试入口及当前覆盖边界见[后端测试基线](../doc/testing/backend-test-baseline.md)。运行还需实际数据库/Redis 配置，构建成功不等同应用已启动。

若依是一套全部开源的快速开发平台，毫无保留给个人及企业免费使用。

* 前端采用Vue、Element（提供 Vue2/Vue3/TS 多版本）。
* 后端采用Spring Boot、Spring Security、Redis & Jwt。
* 基于Jwt的Token认证，支持多终端（Web、App、小程序）无缝接入。
* 支持加载动态权限菜单，多方式轻松权限控制，细粒度到按钮级别。
* 内置强大代码生成器，一键生成前后端代码（java、vue、xml、sql），支持 CRUD 下载。
* 阿里云折扣场：[点我进入](http://aly.ruoyi.vip)，腾讯云秒杀场：[点我进入](http://txy.ruoyi.vip)&nbsp;&nbsp;

# 版本分支

RuoYi-Vue 后端项目提供 Spring Boot 2.x / 3.x / 4.x 多版本分支的并行维护

| 名称              | 说明                      | 地址                                                    |
| :---------------- | :------------------------ | :------------------------------------------------------ |
| master 默认分支   | Spring Boot 4.x (JDK 17+) | https://gitee.com/y_project/RuoYi-Vue                   |
| springboot3 分支  | Spring Boot 3.x (JDK 17+) | https://gitee.com/y_project/RuoYi-Vue/tree/springboot3  |
| springboot2 分支  | Spring Boot 2.x (JDK 8+)  | https://gitee.com/y_project/RuoYi-Vue/tree/springboot2  |  

RuoYi-Vue 前端项目提供 Vue 2.x / 3.x / JavaScript / TypeScript 版本，均可混用搭配

| 项目名称      | **RuoYi-Vue2** | **RuoYi-Vue3** | **RuoYi-Vue3-TypeScript**   |
| :---          | :---           | :---           | :---                        |
| **前端框架**  | Vue 2         | Vue 3          | Vue 3                       |
| **脚本语言**  | JavaScript    | JavaScript     | TypeScript                  |
| **构建工具**  | Vue CLI       | Vite           | Vite                        |
| **UI 组件库** | Element UI    | Element Plus   | Element Plus                |
| **状态管理**  | Vuex          | Pinia          | Pinia                       |
| **路由管理**  | Vue Router 3  | Vue Router 4   | Vue Router 4                |
| **核心特点**  | 1. 技术栈经典稳定<br>2. 社区资料丰富<br>3. 当前维护重心已转移 | 1. 现代前端技术栈<br>2. 开发体验与性能更优<br>3. 官方主推的活跃版本 | 1. 类型加持，减少沟通成本<br>2. 开发时有提示，效率更高<br>3. 多人协作企业级开发项目 |
| **仓库地址**  | [RuoYi-Vue2](https://gitcode.com/yangzongzhuan/RuoYi-Vue2) | [RuoYi-Vue3](https://gitcode.com/yangzongzhuan/RuoYi-Vue3) | [RuoYi-Vue3-TypeScript](https://gitcode.com/yangzongzhuan/RuoYi-Vue3/tree/typescript) |

## 内置功能

1.  用户管理：用户是系统操作者，该功能主要完成系统用户配置。
2.  部门管理：配置系统组织机构（公司、部门、小组），树结构展现支持数据权限。
3.  岗位管理：配置系统用户所属担任职务。
4.  菜单管理：配置系统菜单，操作权限，按钮权限标识等。
5.  角色管理：角色菜单权限分配、设置角色按机构进行数据范围权限划分。
6.  字典管理：对系统中经常使用的一些较为固定的数据进行维护。
7.  参数管理：对系统动态配置常用参数。
8.  通知公告：系统通知公告信息发布维护。
9.  操作日志：系统正常操作日志记录和查询；系统异常信息日志记录和查询。
10. 登录日志：系统登录日志记录查询包含登录异常。
11. 在线用户：当前系统中活跃用户状态监控。
12. 定时任务：在线（添加、修改、删除)任务调度包含执行结果日志。
13. 代码生成：前后端代码的生成（java、html、xml、sql）支持CRUD下载 。
14. 系统接口：根据业务代码自动生成相关的api接口文档。
15. 服务监控：监视当前系统CPU、内存、磁盘、堆栈等相关信息。
16. 缓存监控：对系统的缓存信息查询，命令统计等。
17. 在线构建器：拖动表单元素生成相应的HTML代码。
18. 连接池监视：监视当前系统数据库连接池状态，可进行分析SQL找出系统性能瓶颈。

## 在线体验

- admin/admin123  
- 陆陆续续收到一些打赏，为了更好的体验已用于演示服务器升级。谢谢各位小伙伴。

演示地址：http://vue.ruoyi.vip  
文档地址：http://doc.ruoyi.vip

## 演示图

<table>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/cd1f90be5f2684f4560c9519c0f2a232ee8.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/1cbcf0e6f257c7d3a063c0e3f2ff989e4b3.jpg"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-8074972883b5ba0622e13246738ebba237a.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-9f88719cdfca9af2e58b352a20e23d43b12.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-39bf2584ec3a529b0d5a3b70d15c9b37646.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-936ec82d1f4872e1bc980927654b6007307.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-b2d62ceb95d2dd9b3fbe157bb70d26001e9.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-d67451d308b7a79ad6819723396f7c3d77a.png"/></td>
    </tr>	 
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/5e8c387724954459291aafd5eb52b456f53.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/644e78da53c2e92a95dfda4f76e6d117c4b.jpg"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-8370a0d02977eebf6dbf854c8450293c937.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-49003ed83f60f633e7153609a53a2b644f7.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-d4fe726319ece268d4746602c39cffc0621.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-c195234bbcd30be6927f037a6755e6ab69c.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/b6115bc8c31de52951982e509930b20684a.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-5e4daac0bb59612c5038448acbcef235e3a.png"/></td>
    </tr>
</table>


## 若依前后端分离交流群

QQ群： [![加入QQ群](https://img.shields.io/badge/已满-937441-blue.svg)](https://jq.qq.com/?_wv=1027&k=5bVB1og) [![加入QQ群](https://img.shields.io/badge/已满-887144332-blue.svg)](https://jq.qq.com/?_wv=1027&k=5eiA4DH) [![加入QQ群](https://img.shields.io/badge/已满-180251782-blue.svg)](https://jq.qq.com/?_wv=1027&k=5AxMKlC) [![加入QQ群](https://img.shields.io/badge/已满-104180207-blue.svg)](https://jq.qq.com/?_wv=1027&k=51G72yr) [![加入QQ群](https://img.shields.io/badge/已满-186866453-blue.svg)](https://jq.qq.com/?_wv=1027&k=VvjN2nvu) [![加入QQ群](https://img.shields.io/badge/已满-201396349-blue.svg)](https://jq.qq.com/?_wv=1027&k=5vYAqA05) [![加入QQ群](https://img.shields.io/badge/已满-101456076-blue.svg)](https://jq.qq.com/?_wv=1027&k=kOIINEb5) [![加入QQ群](https://img.shields.io/badge/已满-101539465-blue.svg)](https://jq.qq.com/?_wv=1027&k=UKtX5jhs) [![加入QQ群](https://img.shields.io/badge/已满-264312783-blue.svg)](https://jq.qq.com/?_wv=1027&k=EI9an8lJ) [![加入QQ群](https://img.shields.io/badge/已满-167385320-blue.svg)](https://jq.qq.com/?_wv=1027&k=SWCtLnMz) [![加入QQ群](https://img.shields.io/badge/已满-104748341-blue.svg)](https://jq.qq.com/?_wv=1027&k=96Dkdq0k) [![加入QQ群](https://img.shields.io/badge/已满-160110482-blue.svg)](https://jq.qq.com/?_wv=1027&k=0fsNiYZt) [![加入QQ群](https://img.shields.io/badge/已满-170801498-blue.svg)](https://jq.qq.com/?_wv=1027&k=7xw4xUG1) [![加入QQ群](https://img.shields.io/badge/已满-108482800-blue.svg)](https://jq.qq.com/?_wv=1027&k=eCx8eyoJ) [![加入QQ群](https://img.shields.io/badge/已满-101046199-blue.svg)](https://jq.qq.com/?_wv=1027&k=SpyH2875) [![加入QQ群](https://img.shields.io/badge/已满-136919097-blue.svg)](https://jq.qq.com/?_wv=1027&k=tKEt51dz) [![加入QQ群](https://img.shields.io/badge/已满-143961921-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=0vBbSb0ztbBgVtn3kJS-Q4HUNYwip89G&authKey=8irq5PhutrZmWIvsUsklBxhj57l%2F1nOZqjzigkXZVoZE451GG4JHPOqW7AW6cf0T&noverify=0&group_code=143961921) [![加入QQ群](https://img.shields.io/badge/已满-174951577-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=ZFAPAbp09S2ltvwrJzp7wGlbopsc0rwi&authKey=HB2cxpxP2yspk%2Bo3WKTBfktRCccVkU26cgi5B16u0KcAYrVu7sBaE7XSEqmMdFQp&noverify=0&group_code=174951577) [![加入QQ群](https://img.shields.io/badge/已满-161281055-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Fn2aF5IHpwsy8j6VlalNJK6qbwFLFHat&authKey=uyIT%2B97x2AXj3odyXpsSpVaPMC%2Bidw0LxG5MAtEqlrcBcWJUA%2FeS43rsF1Tg7IRJ&noverify=0&group_code=161281055) [![加入QQ群](https://img.shields.io/badge/已满-138988063-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=XIzkm_mV2xTsUtFxo63bmicYoDBA6Ifm&authKey=dDW%2F4qsmw3x9govoZY9w%2FoWAoC4wbHqGal%2BbqLzoS6VBarU8EBptIgPKN%2FviyC8j&noverify=0&group_code=138988063) [![加入QQ群](https://img.shields.io/badge/已满-151450850-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=DkugnCg68PevlycJSKSwjhFqfIgrWWwR&authKey=pR1Pa5lPIeGF%2FFtIk6d%2FGB5qFi0EdvyErtpQXULzo03zbhopBHLWcuqdpwY241R%2F&noverify=0&group_code=151450850) [![加入QQ群](https://img.shields.io/badge/已满-224622315-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=F58bgRa-Dp-rsQJThiJqIYv8t4-lWfXh&authKey=UmUs4CVG5OPA1whvsa4uSespOvyd8%2FAr9olEGaWAfdLmfKQk%2FVBp2YU3u2xXXt76&noverify=0&group_code=224622315) [![加入QQ群](https://img.shields.io/badge/已满-287842588-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Nxb2EQ5qozWa218Wbs7zgBnjLSNk_tVT&authKey=obBKXj6SBKgrFTJZx0AqQnIYbNOvBB2kmgwWvGhzxR67RoRr84%2Bus5OadzMcdJl5&noverify=0&group_code=287842588) [![加入QQ群](https://img.shields.io/badge/已满-187944233-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=numtK1M_I4eVd2Gvg8qtbuL8JgX42qNh&authKey=giV9XWMaFZTY%2FqPlmWbkB9g3fi0Ev5CwEtT9Tgei0oUlFFCQLDp4ozWRiVIzubIm&noverify=0&group_code=187944233) [![加入QQ群](https://img.shields.io/badge/已满-228578329-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=G6r5KGCaa3pqdbUSXNIgYloyb8e0_L0D&authKey=4w8tF1eGW7%2FedWn%2FHAypQksdrML%2BDHolQSx7094Agm7Luakj9EbfPnSTxSi2T1LQ&noverify=0&group_code=228578329) [![加入QQ群](https://img.shields.io/badge/已满-191164766-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=GsOo-OLz53J8y_9TPoO6XXSGNRTgbFxA&authKey=R7Uy%2Feq%2BZsoKNqHvRKhiXpypW7DAogoWapOawUGHokJSBIBIre2%2FoiAZeZBSLuBc&noverify=0&group_code=191164766) [![加入QQ群](https://img.shields.io/badge/已满-174569686-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=PmYavuzsOthVqfdAPbo4uAeIbu7Ttjgc&authKey=p52l8%2FXa4PS1JcEmS3VccKSwOPJUZ1ZfQ69MEKzbrooNUljRtlKjvsXf04bxNp3G&noverify=0&group_code=174569686) [![加入QQ群](https://img.shields.io/badge/已满-127358632-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=M9y5NjAl44lAL_Vh2crmEehZU_PMU6KS&authKey=ZSDz8hEREWSaPuxQV3gEwqGIaGjfRNnkB4rJjf0IvXhrSUGSGwQFmBA%2Boe8HFxyl&noverify=0&group_code=127358632) [![加入QQ群](https://img.shields.io/badge/113071109-blue.svg)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=UGnoqRcFRxUaX-JAUNrSDbPimKrllp4x&authKey=Bbzf8Bc0XaQhq5c4QOg46Uqngd%2FvtIixrLvCM1EkPf41diqKXKZqXVwAxHTxJ8R1&noverify=0&group_code=113071109) 点击按钮入群。

登录验证码使用原子 GETDEL，要求 Redis 6.2+（当前验证为 7.4.11）；同一验证码只能被一个并发请求消费。密码失败采用 Redis Lua 原子计数，达到上限后不延长锁定，并使用数据库账号名统一大小写输入；正确密码不能清除已由并发失败触发的锁定。锁定时长保留当前默认 10 分钟，可配置。

管理员临时解锁接口：`PUT /system/user/unlock/{userId}`，必须有 `system:user:unlock` 权限，保留审计。只删除指定账号密码失败状态，不修改密码、停用或删除标志；不存在/已删除账号拒绝操作。权限通过增量迁移注册并仅分配给管理员，普通/只读角色不自动获权。前端入口等待参考交互资料，尚未实现。
