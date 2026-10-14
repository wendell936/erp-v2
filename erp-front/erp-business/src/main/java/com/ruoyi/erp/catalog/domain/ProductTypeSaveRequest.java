package com.ruoyi.erp.catalog.domain;

import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import com.ruoyi.common.xss.Xss;
public class ProductTypeSaveRequest
{
    public interface Create extends Default { }
    public interface Update extends Default { }

    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "[A-Za-z0-9]+", message = "类型码仅允许英文字母和数字")
    private String typeCode;

    @NotBlank
    @Size(max = 200)
    @Xss
    private String typeName;

    @Size(max = 500)
    @Xss
    private String keywords;

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
