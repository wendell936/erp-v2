package com.ruoyi.erp.supplier.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
public class Supplier extends BaseEntity
{

    private Long supplierId;


    private String supplierName;


    private String contactName;


    private String contactPhone;


    private String settlementTerms;


    private String status;


    private Long version;

    @JsonIgnore
    private String requestId;

    @JsonIgnore
    private String requestHash;
    public Long getSupplierId()
    {
        return supplierId;
    }

    public void setSupplierId(Long supplierId)
    {
        this.supplierId = supplierId;
    }

    public String getSupplierName()
    {
        return supplierName;
    }

    public void setSupplierName(String supplierName)
    {
        this.supplierName = supplierName;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }

    public String getSettlementTerms()
    {
        return settlementTerms;
    }

    public void setSettlementTerms(String settlementTerms)
    {
        this.settlementTerms = settlementTerms;
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
