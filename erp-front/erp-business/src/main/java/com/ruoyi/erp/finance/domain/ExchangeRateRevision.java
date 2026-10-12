package com.ruoyi.erp.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;

public class ExchangeRateRevision
{
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long revisionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long rateId;

    private LocalDate rateDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal usdCny;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long version;

    private String reason;

    private String operator;

    private Date changeTime;

    public Long getRevisionId()
    {
        return revisionId;
    }

    public void setRevisionId(Long revisionId)
    {
        this.revisionId = revisionId;
    }

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

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public Date getChangeTime()
    {
        return changeTime;
    }

    public void setChangeTime(Date changeTime)
    {
        this.changeTime = changeTime;
    }

}
