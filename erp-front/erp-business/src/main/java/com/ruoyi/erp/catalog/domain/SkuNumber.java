package com.ruoyi.erp.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
public class SkuNumber
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long numberId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long typeId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long mainNumberId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sequenceNumber;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastVariantNumber;

    private String skuCode;

    @JsonIgnore
    private String requestId;

    private String createBy;

    private java.util.Date createTime;

    public Long getNumberId()
    {
        return numberId;
    }

    public void setNumberId(Long numberId)
    {
        this.numberId = numberId;
    }

    public Long getTypeId()
    {
        return typeId;
    }

    public void setTypeId(Long typeId)
    {
        this.typeId = typeId;
    }

    public Long getMainNumberId()
    {
        return mainNumberId;
    }

    public void setMainNumberId(Long mainNumberId)
    {
        this.mainNumberId = mainNumberId;
    }

    public Long getSequenceNumber()
    {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber)
    {
        this.sequenceNumber = sequenceNumber;
    }

    public Long getLastVariantNumber()
    {
        return lastVariantNumber;
    }

    public void setLastVariantNumber(Long lastVariantNumber)
    {
        this.lastVariantNumber = lastVariantNumber;
    }

    public String getSkuCode()
    {
        return skuCode;
    }

    public void setSkuCode(String skuCode)
    {
        this.skuCode = skuCode;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public java.util.Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(java.util.Date createTime)
    {
        this.createTime = createTime;
    }
}
