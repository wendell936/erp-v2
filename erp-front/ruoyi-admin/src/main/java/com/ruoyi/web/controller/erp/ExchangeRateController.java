package com.ruoyi.web.controller.erp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.erp.finance.domain.ConversionRequest;
import com.ruoyi.erp.finance.domain.ExchangeRateRequest;
import com.ruoyi.erp.finance.service.ExchangeRateService;

@RestController
@RequestMapping("/erp/exchange-rate")
public class ExchangeRateController extends BaseController
{
    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String from, @RequestParam(required = false) String to)
    {
        startPage("rate_date desc", "rate_id");
        try
        {
            return getDataTable(service.list(from, to));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:query')")
    @GetMapping("/{rateId}")
    public AjaxResult get(@PathVariable Long rateId)
    {
        return success(service.get(rateId));
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:query')")
    @GetMapping("/{rateId}/revisions")
    public TableDataInfo revisions(@PathVariable Long rateId)
    {
        service.get(rateId);
        startPage("version desc", "revision_id");
        try
        {
            return getDataTable(service.revisions(rateId));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:add')")
    @Log(title = "日期汇率新增", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult create(@RequestBody ExchangeRateRequest request)
    {
        return success(service.create(request));
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:edit')")
    @Log(title = "日期汇率修改", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{rateId}")
    public AjaxResult update(@PathVariable Long rateId, @RequestBody ExchangeRateRequest request)
    {
        return success(service.update(rateId, request));
    }

    @PreAuthorize("@ss.hasPermi('erp:exchangeRate:query')")
    @PostMapping("/preview")
    public AjaxResult preview(@RequestBody ConversionRequest request)
    {
        return success(service.snapshot(request));
    }
}
