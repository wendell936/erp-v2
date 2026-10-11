package com.ruoyi.erp.supplier.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.erp.supplier.domain.Supplier;
import com.ruoyi.erp.supplier.domain.SupplierChange;

public interface SupplierMapper
{
    List<Supplier> selectList(@Param("supplierName") String supplierName, @Param("status") String status);
    Supplier selectById(Long supplierId);
    int insertOrFind(Supplier supplier);
    int update(Supplier supplier);
    int updateStatus(Supplier supplier);
    int insertChange(SupplierChange change);
    int insertCreationChange(SupplierChange change);
    List<SupplierChange> selectChanges(Long supplierId);
}
