package com.ruoyi.erp.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 不可变换算值。确认业务须在自身事务持久化全部字段，不得随后按最新汇率重算。 */
public record CurrencySnapshot(String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal originalAmount,
        LocalDate rateDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long rateId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long rateVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal usdCny,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cnyAmount)
{
}
