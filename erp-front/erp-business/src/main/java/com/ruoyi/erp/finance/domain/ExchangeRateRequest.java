package com.ruoyi.erp.finance.domain;

/** 小数与日期使用字符串输入，禁止浏览器浮点或服务器默认日期参与金额计算。 */
public record ExchangeRateRequest(String rateDate, String usdCny, String requestId, Long version, String reason)
{
}
