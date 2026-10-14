package com.ruoyi.erp.catalog.domain;

import java.util.Date;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public class ProductTypeChange
{

    @JsonSerialize(using = ToStringSerializer.class)
    private Long changeId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long typeId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long version;

    private String operation;

    private String reason;

    private String operator;

    private Date changeTime;
    public Long getChangeId()
    {
        return changeId;
    }

    public void setChangeId(Long changeId)
    {
        this.changeId = changeId;
    }

    public Long getTypeId()
    {
        return typeId;
    }

    public void setTypeId(Long typeId)
    {
        this.typeId = typeId;
    }

    public Long getVersion()
    {
        return version;
    }

    public void setVersion(Long version)
    {
        this.version = version;
    }

    public String getOperation()
    {
        return operation;
    }

    public void setOperation(String operation)
    {
        this.operation = operation;
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
