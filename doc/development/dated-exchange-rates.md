# 日期汇率与原币换算

任务：P1-18.a、P1-19；依据财务-01/02及用户2026-10-11确认。单公司、USD/CNY，手工维护“1 USD = X CNY”；无外部汇率服务或新增依赖。

## 规则与精度

- 指定汇率日期精确匹配，缺当日汇率报待补，不沿用前日、不用当前日期或零。日期为DATE及ISO `yyyy-MM-dd`，支持数据库1000～9999年，不转换时区、不代表店铺/财务时区已确认。
- 汇率DECIMAL(16,8)，大于0，最大`99999999.99999999`；输入普通十进制，最多8位整数及8位小数，不接受指数、NaN或静默截断。相同日期唯一，新增UUID幂等；修改日期须另新增，不提供删除或停用以免引用丢失。
- 换算入参为原币金额字符串，最多12位整数、2位小数，绝对值最大`999999999999.99`；CNY/USD，允许负数供退款等换算参考，不因此授权未来单据接受负费用。服务使用BigDecimal精确相乘后按分HALF_UP，包括负数半分向远离零舍入；结果最多20位整数+2位小数，未来持久化字段至少DECIMAL(22,2)。金额/汇率/ID/版本以JSON字符串返回，前端不转Number或自行计算金额。
- CNY直接保留原币金额，系数1，不查USD汇率，汇率日期、ID及版本为空；USD必须指定日期并返回实际汇率版本。
- 同事务修改当前值并追加版本审计，保存当时汇率值、日期、原因、维护人及UTC绝对时间。历史版本不覆盖；业务确认须存下当时快照，之后改汇率不重算旧业务。

## 接口与权限

路径前缀 `/erp/exchange-rate`，普通响应沿用AjaxResult，列表沿用TableDataInfo。

| 方法/路径 | 输入与结果 | 权限 |
|---|---|---|
| GET `/list` | 可选from/to日期范围，默认10最多100，服务端按日期降序和唯一rate_id排序 | `erp:exchangeRate:list` |
| GET `/{id}` | 当前值及维护字段，创建请求标识/摘要不输出 | `erp:exchangeRate:query` |
| GET `/{id}/revisions` | 版本降序、revision_id唯一排序的分页历史 | `erp:exchangeRate:query` |
| POST 根路径 | rateDate、usdCny、UUID requestId；创建审计人/时间由服务端生成 | `erp:exchangeRate:add` |
| PUT `/{id}` | 原rateDate、新usdCny、当前version、纯文本reason（1～500字） | `erp:exchangeRate:edit` |
| POST `/preview` | currency、originalAmount、USD的rateDate；返回不可变CurrencySnapshot | `erp:exchangeRate:query` |

管理员和操作角色可维护，全公司范围；只读仅列表、详情、版本及预览。预览POST没有任何业务写入，不记费用/库存/资金、不产生修订。维护通用操作日志不保存请求/响应正文；技术日志不输出SQL绑定参数及完整数据库异常，保留操作、ID、异常类型、SQLState和vendorCode定位。

新增同UUID、相同规范日期/金额及人员重试返回同一当前记录，不重复创建或审计；修改后重试原新增仍不改写版本0。复用UUID但内容/人员不同拒绝；不同UUID重复日期拒绝并提示编辑现有记录。并发旧版本修改仅一项成功，失败不自动替换版本重试；编辑重复提交以版本冲突拒绝，不追加重复修订。

## 页面

入口“基础资料 → 日期汇率”，组件 `erp/exchange-rate/index`。日期范围、列表、行内编辑与详情历史沿用[领星观察](lingxing-interaction-reference.md)；本项目按日、手工USD/CNY、版本及原因管理，不采用领星月度、多币种和官方同步规则。

新增保留UUID以便失败重试，保存期间禁用输入/重复按钮/关闭；旧版本失败保留输入并提示重新读取核对。关闭或离开未保存表单提示放弃确认。预览改变输入先清旧结果，缺汇率不显示为0；结果明确币种、汇率日期/值/版本和人民币金额，并说明仅供参考。

## 迁移与恢复

唯一来源：[V2026101104](../../erp-front/ruoyi-admin/src/main/resources/db/migration/V2026101104__dated_exchange_rates.sql)新增当前汇率、版本表与4项权限；[V2026101105](../../erp-front/ruoyi-admin/src/main/resources/db/migration/V2026101105__exchange_rate_page_menu.sql)保留权限ID注册页面。前置供应商菜单V2026101103，无历史业务迁移或金额回算。

首次DDL失败不保证事务回滚，按[数据库规范](database-migrations.md)阻断发布、保护现场、核对实际语句与Flyway历史、备份恢复或前向修复；不自动repair/clean。旧应用回退保留表和审计；正式环境执行另按发布授权，不在开发任务中接管真实库。业务版本同时关联汇率ID与版本，版本表按(rate_id,version)唯一且外键保护当前记录。

原供应商UTC DATETIME handler已提升至 `com.ruoyi.erp.persistence.UtcDateTimeTypeHandler` 供两模块复用，接口及存储行为不变，供应商回归必须随此调整验证。

## 验证与剩余边界

`ExchangeRateServiceTest`覆盖精确半分、正负/大数/最小汇率、缺精确日期、不变快照、CNY无需汇率及输入边界；`ExchangeRateFlowIT`在真实MySQL/Redis/HTTP覆盖三角色、字符串小数、并发重试/唯一日期、旧版本竞争、版本历史、审计失败全回滚与恢复、DATE/UTC、分页/约束、重复迁移和真实菜单路由。最新结果与日志摘要见[执行记录](execution-journal.md)，页面实际验证见[页面验收](../testing/dated-exchange-rates-ui.md)。

P1-18.b待采购/费用确认模块：在该业务事务内通过服务端生成并持久化原币、汇率日期、ID、版本、值及人民币金额，不接受客户端传入的预览值直接作为已确认事实。本批只实现不可变快照服务及预览，不预建无来源的财务流水或宣称实际费用快照入账已完成。单价/数量的高精度计算与分摊尾差仍由对应业务任务确认，不用本接口金额2位限制代替未来采购单价规则。
