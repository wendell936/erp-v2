package com.ruoyi.web.controller.erp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.erp.catalog.domain.ProductTypeSaveRequest;
import com.ruoyi.erp.catalog.domain.ProductTypeStatusRequest;
import com.ruoyi.erp.catalog.service.ProductTypeService;

@RestController
@RequestMapping("/erp/product-type")
public class ProductTypeController extends BaseController
{
    private final ProductTypeService service;

    public ProductTypeController(ProductTypeService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String typeName,
            @RequestParam(required = false) String status)
    {
        startPage("type_id desc", "type_id");
        try
        {
            return getDataTable(service.list(typeName, status));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:query')")
    @GetMapping("/{typeId}")
    public AjaxResult get(@PathVariable Long typeId)
    {
        return success(service.get(typeId));
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:query')")
    @GetMapping("/{typeId}/changes")
    public TableDataInfo changes(@PathVariable Long typeId)
    {
        service.get(typeId);
        startPage("version desc", "change_id");
        try
        {
            return getDataTable(service.changes(typeId));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:add')")
    @Log(title = "产品类型新增", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult create(@RequestBody ProductTypeSaveRequest request)
    {
        return success(service.create(request));
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:edit')")
    @Log(title = "产品类型编辑", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{typeId}")
    public AjaxResult update(@PathVariable Long typeId, @RequestBody ProductTypeSaveRequest request)
    {
        return success(service.update(typeId, request));
    }

    @PreAuthorize("@ss.hasPermi('erp:productType:status')")
    @Log(title = "产品类型启停", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{typeId}/status")
    public AjaxResult status(@PathVariable Long typeId, @RequestBody ProductTypeStatusRequest request)
    {
        return success(service.status(typeId, request));
    }
}
