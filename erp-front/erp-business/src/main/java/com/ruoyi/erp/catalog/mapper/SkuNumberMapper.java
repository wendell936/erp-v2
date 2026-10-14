package com.ruoyi.erp.catalog.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.erp.catalog.domain.ProductType;
import com.ruoyi.erp.catalog.domain.SkuNumber;

public interface SkuNumberMapper
{
    ProductType lockType(Long typeId);
    SkuNumber selectByRequest(String requestId);
    SkuNumber selectById(Long numberId);
    SkuNumber lockNumber(Long numberId);
    int advanceMain(@Param("typeId") Long typeId, @Param("lastNumber") Long lastNumber);
    int advanceVariant(@Param("numberId") Long numberId, @Param("lastNumber") Long lastNumber);
    int insert(SkuNumber number);
}
