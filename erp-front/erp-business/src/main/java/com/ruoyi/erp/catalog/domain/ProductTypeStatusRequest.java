package com.ruoyi.erp.catalog.domain;

import jakarta.validation.constraints.*;
import com.ruoyi.common.xss.Xss;
public class ProductTypeStatusRequest
{
    @NotNull
    @Pattern(regexp = "[01]", message = "状态仅允许0启用或1停用")
    private String status;

    @NotNull
    @PositiveOrZero
    @Max(9223372036854775806L)
    private Long version;

    @NotBlank
    @Size(max = 500)
    @Xss
    private String reason;
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

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
