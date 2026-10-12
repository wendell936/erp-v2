package com.ruoyi.erp.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;

public class ExchangeRate extends BaseEntity
{
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long rateId;

    private LocalDate rateDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal usdCny;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long version;

    @JsonIgnore
    private String requestId;

    @JsonIgnore
    private String requestHash;

    public Long getRateId()
    {
        return rateId;
    }

    public void setRateId(Long rateId)
    {
        this.rateId = rateId;
    }

    public LocalDate getRateDate()
    {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate)
    {
        this.rateDate = rateDate;
    }

    public BigDecimal getUsdCny()
    {
        return usdCny;
    }

    public void setUsdCny(BigDecimal usdCny)
    {
        this.usdCny = usdCny;
    }

    public Long getVersion()
    {
        return version;
    }

    public void setVersion(Long version)
    {
        this.version = version;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public String getRequestHash()
    {
        return requestHash;
    }

    public void setRequestHash(String requestHash)
    {
        this.requestHash = requestHash;
    }

}
