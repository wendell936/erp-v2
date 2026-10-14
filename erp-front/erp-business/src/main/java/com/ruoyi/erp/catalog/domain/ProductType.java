package com.ruoyi.erp.catalog.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
public class ProductType extends BaseEntity
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long typeId;

    private String typeCode;

    private String typeName;

    private String keywords;

    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long version;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastNumber;

    @JsonIgnore
    private String requestId;

    @JsonIgnore
    private String requestHash;

    public Long getTypeId()
    {
        return typeId;
    }

    public void setTypeId(Long typeId)
    {
        this.typeId = typeId;
    }

    public String getTypeCode()
    {
        return typeCode;
    }

    public void setTypeCode(String typeCode)
    {
        this.typeCode = typeCode;
    }

    public String getTypeName()
    {
        return typeName;
    }

    public void setTypeName(String typeName)
    {
        this.typeName = typeName;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getVersion()
    {
        return version;
    }

    public void setVersion(Long version)
    {
        this.version = version;
    }

    public Long getLastNumber()
    {
        return lastNumber;
    }

    public void setLastNumber(Long lastNumber)
    {
        this.lastNumber = lastNumber;
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
