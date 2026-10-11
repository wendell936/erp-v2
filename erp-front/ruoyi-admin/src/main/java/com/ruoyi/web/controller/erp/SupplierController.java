package com.ruoyi.web.controller.erp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.erp.supplier.domain.SupplierSaveRequest;
import com.ruoyi.erp.supplier.domain.SupplierStatusRequest;
import com.ruoyi.erp.supplier.service.SupplierService;

@RestController
@RequestMapping("/erp/supplier")
public class SupplierController extends BaseController
{
    private final SupplierService service;

    public SupplierController(SupplierService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status)
    {
        startPage("supplier_id desc", "supplier_id");
        try
        {
            return getDataTable(service.list(supplierName, status));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:query')")
    @GetMapping("/{supplierId}")
    public AjaxResult get(@PathVariable Long supplierId)
    {
        return success(service.get(supplierId));
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:query')")
    @GetMapping("/{supplierId}/changes")
    public TableDataInfo changes(@PathVariable Long supplierId)
    {
        service.get(supplierId);
        startPage("version desc", "change_id");
        try
        {
            return getDataTable(service.changes(supplierId));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:add')")
    @Log(title = "供应商新增", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult create(@RequestBody SupplierSaveRequest request)
    {
        return success(service.create(request));
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:edit')")
    @Log(title = "供应商编辑", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{supplierId}")
    public AjaxResult update(@PathVariable Long supplierId, @RequestBody SupplierSaveRequest request)
    {
        return success(service.update(supplierId, request));
    }

    @PreAuthorize("@ss.hasPermi('erp:supplier:status')")
    @Log(title = "供应商启停", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{supplierId}/status")
    public AjaxResult status(@PathVariable Long supplierId, @RequestBody SupplierStatusRequest request)
    {
        return success(service.status(supplierId, request));
    }
}
