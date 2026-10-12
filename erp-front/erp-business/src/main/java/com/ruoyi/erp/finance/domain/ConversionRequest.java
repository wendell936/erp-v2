package com.ruoyi.erp.finance.domain;

/** 仅换算预览，不创建费用、库存或资金事实。 */
public record ConversionRequest(String currency, String originalAmount, String rateDate)
{
}
