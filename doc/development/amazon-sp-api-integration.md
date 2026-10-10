# Amazon SP-API 接入与配置

更新日期：2026-10-11。任务：P0-20、P0-21。实际验证与提交见[执行记录](execution-journal.md)，范围见[开发计划](development-plan.md)。

## 当前实现

采用 Amazon 官方 `software.amazon.spapi:spapi-sdk:1.13.0`，固定版本并保留官方接口、数据模型、解析和按操作限流。当前仅装配 Sellers 客户端，未增加同步任务、HTTP 管理接口、定时取数或 Amazon 写操作。启用配置只创建客户端，不在启动时发出网络请求。

SDK 中实际核对了 Catalog 2022-04-01、Listings 2021-08-01、FBA Inventory v1、Fulfillment Inbound 2024-03-20、Orders 2026-01-01、Finances 2024-06-19、Reports 2021-06-30 和 Sellers v1。存在对应类不代表店铺角色已获批、操作已联通或业务映射已实现。首期货件仍在 Seller Central 创建，ERP 后续关联同步。

官方 SDK 为 Apache-2.0，要求 Java 11+；本项目继续声明 Java 17。本轮构建及测试实际使用 JDK 21.0.11、目标字节码 17，没有 Java 17 运行时验证结果。版本选择基于目标接口覆盖及当前依赖验证，不以“最新”为选型依据。

## 依赖与兼容处理

| 依赖 | 当前解析版本 | 用途及处理 |
|---|---|---|
| spapi-sdk | 1.13.0 | 官方客户端和模型；升级时复核生成方法签名、接口版本和认证扩展点 |
| OkHttp / logging-interceptor | 4.12.0 | 沿用 SDK HTTP 实现，直接声明 OkHttp 以配置传输；未启用 HTTP 日志拦截器 |
| Gson | 2.13.2 | SDK 及 LWA JSON 解析；沿用 Boot BOM，不使用 SDK 较旧的声明版本 |
| gson-fire | 1.9.0 | SDK 模型适配，保留传递依赖 |
| Okio / okio-jvm | 3.9.1 | SDK/OkHttp 传递依赖 |
| Bucket4j JDK11 core | 8.14.0 | 保留 SDK 操作限流；客户端为单例 |
| Kotlin stdlib | 2.3.21 | Boot BOM 管理，当前模拟调用已验证加载 |
| SnakeYAML | 2.6 | Boot BOM 管理，用于 SDK 内部限流资源读取 |
| Jakarta Annotation API | 3.0.0 | Boot BOM 管理 |
| swagger-annotations-jakarta | 2.2.52 | 与已有 springdoc 一致；排除 SDK 的旧 swagger-annotations，消除同包同名注解类重复 |

新增直接依赖复用 SDK 所需的 HTTP/JSON 组件，没有引入第二套 SP-API 客户端。SDK、OkHttp、Gson、gson-fire、Okio、Bucket4j、Kotlin、SnakeYAML 和 Swagger 注解发布 POM/父 POM 标明 Apache-2.0；Jakarta Annotation API 标明 EPL-2.0 / GPL2 with Classpath Exception 双许可，使用时保留相应许可证通知。完整依赖安全扫描作为发布前检查，不能用构建成功或上游暂未公布公告证明无漏洞。

已查阅官方 SDK 安全页面及源码。本轮评估重点为凭据日志泄漏、超时、重定向、并发刷新和重复依赖；没有执行完整 CVE 扫描、Java 17 运行或真实 TLS/网络超时演练。升级成本主要是重新验证模型解析、公开 signer 扩展、依赖树和同步业务映射，不修改 SDK 生成源码。

## 为什么补认证适配

对本地 1.13.0 sources.jar 的检查发现：官方 LWA 客户端引用内部全局默认 HTTP 客户端，没有公开超时注入入口；默认令牌缓存先查后刷新，没有刷新互斥；凭据对象 `toString()` 含敏感字段。仅配置 API HTTP 客户端不能同时配置内部 LWA 传输。

项目通过公开的 `ApiClient.setLWAAuthorizationSigner` 和 signer 子类扩展，只补 LWA 令牌交换：

- 使用相同 OkHttp 实现，按官方要求以表单提交 refresh token；固定 LWA 和北美区域 HTTPS 地址，禁止自动重定向，避免凭据或 access token 被转发到其他地址。
- 单实例内存缓存，按请求开始时间及有效期计算提前 60 秒刷新；整个读取/刷新过程互斥，合并并发成功刷新。短于等于 60 秒的令牌不复用；失败不缓存，不退回已过期令牌。
- 连接默认 10 秒、读写 30 秒、单次调用 45 秒；超时配置限制为 1ms～2m，避免零值导致无限等待。关闭底层自动连接重试；同步层后续按任务做有界重试和恢复。
- 限制响应大小，校验令牌格式及正整数有效期；错误仅保留分类和 HTTP 状态，不携带凭据、响应正文或原始异常文本。保留 `AUTHORIZATION`、`THROTTLED`、`REMOTE_SERVICE`、`INVALID_RESPONSE`、`NETWORK` 分类。

该适配不申请 RDT，不采集买家个人信息。单实例互斥不提供跨进程令牌锁；当前模块化单体场景适用，未来部署方式变化再评估。当前错误分类只用于 LWA；官方 API 原始异常可能带响应正文，后续业务 gateway 必须安全映射后才能进入任务日志或页面，禁止直接记录整个 SDK 异常/模型。

## 安全配置

应用入口配置位于 `erp-front/ruoyi-admin/src/main/resources/application.yml`，默认关闭。

| 环境变量 | 内容 |
|---|---|
| `AMAZON_SP_API_ENABLED` | 默认 false；显式 true 时要求三个凭据齐全 |
| `AMAZON_LWA_CLIENT_ID` | 自有应用 LWA client ID |
| `AMAZON_LWA_CLIENT_SECRET` | LWA client secret |
| `AMAZON_LWA_REFRESH_TOKEN` | 已自授权店铺的 refresh token |

连接、读写和调用超时可通过对应 Spring 外部配置覆盖。真实值通过环境或受保护的配置注入，不放仓库、聊天、前端或普通系统参数；配置对象输出已隐藏凭据。当前不提供在线改密钥功能，轮换后重启客户端实例。

只支持当前美国站北美生产区域地址，未开放任意 endpoint 配置。测试使用 HTTP 拦截器合成响应，既不访问生产地址，也不以静态沙箱替代店铺验证。Seller ID、Marketplace 参与状态、权限角色、店铺/财务时区及启用日期留待对应任务核对。

## 验证入口与边界

从仓库根目录执行：

```text
mvn -B -f erp-front/pom.xml -pl erp-business -am test
mvn -B -f erp-front/pom.xml verify
```

`AmazonLwaTokenProviderTest` 覆盖缓存、提前刷新、并发、过期后失败、恢复、失败分类、无效/超长响应、网络异常脱敏和签名地址限制。`AmazonSpApiConfigurationTest` 覆盖默认关闭、缺凭据失败、配置绑定、单例与传输约束。`AmazonSdkCompatibilityTest` 使用真实生成的 Sellers 请求/模型，验证解析、request ID、限流、失败和目标类加载。全部网络由测试拦截器替换，不读取真实环境凭据。

模拟测试不证明真实 Amazon 授权、角色、限流配额、店铺数据对账或数据库事务正确。P0-22/24 与后续同步任务继续承担这些验收；真实接口成功之前不将对接标记为上线可用。

官方依据：[Java SDK](https://github.com/amzn/selling-partner-api-sdk/tree/main/java)、[SDK 安全说明](https://github.com/amzn/selling-partner-api-sdk/security)、[LWA 与 SP-API 连接流程](https://developer-docs.amazon.com/sp-api/docs/connecting-to-the-selling-partner-api)。
