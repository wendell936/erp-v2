package com.ruoyi.erp.supplier.domain;

import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import com.ruoyi.common.xss.Xss;
public class SupplierSaveRequest
{
    public interface Create extends Default { }
    public interface Update extends Default { }

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 200)
    @Xss
    private String supplierName;

    @Size(max = 100)
    @Xss
    private String contactName;

    @Size(max = 100)
    @Xss
    private String contactPhone;

    @Size(max = 1000)
    @Xss
    private String settlementTerms;

    @Size(max = 500)
    @Xss
    private String remark;

    @NotBlank(groups = Create.class)
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String requestId;

    @NotNull(groups = Update.class)
    @PositiveOrZero
    @Max(9223372036854775806L)
    private Long version;

    @NotBlank(groups = Update.class)
    @Size(max = 500)
    @Xss
    private String reason;
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

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
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
}
