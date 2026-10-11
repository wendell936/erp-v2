package com.ruoyi.erp.supplier.domain;

import java.util.Date;
public class SupplierChange
{

    private Long changeId;


    private Long supplierId;


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

    public Long getSupplierId()
    {
        return supplierId;
    }

    public void setSupplierId(Long supplierId)
    {
        this.supplierId = supplierId;
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
