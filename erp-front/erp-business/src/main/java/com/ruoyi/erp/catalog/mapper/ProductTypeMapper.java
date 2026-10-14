package com.ruoyi.erp.catalog.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.erp.catalog.domain.ProductType;
import com.ruoyi.erp.catalog.domain.ProductTypeChange;

public interface ProductTypeMapper
{
    List<ProductType> selectList(@Param("typeName") String typeName, @Param("status") String status);
    ProductType selectById(Long typeId);
    int insertOrFind(ProductType productType);
    int update(ProductType productType);
    int updateStatus(ProductType productType);
    int insertChange(ProductTypeChange change);
    int insertCreationChange(ProductTypeChange change);
    List<ProductTypeChange> selectChanges(Long typeId);
}
