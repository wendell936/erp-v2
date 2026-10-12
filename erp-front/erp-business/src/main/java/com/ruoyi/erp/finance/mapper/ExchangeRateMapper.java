package com.ruoyi.erp.finance.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.erp.finance.domain.ExchangeRate;
import com.ruoyi.erp.finance.domain.ExchangeRateRevision;

public interface ExchangeRateMapper
{
    List<ExchangeRate> selectList(@Param("from") LocalDate from, @Param("to") LocalDate to);
    ExchangeRate selectById(Long rateId);
    ExchangeRate selectByDate(LocalDate rateDate);
    int insertOrFind(ExchangeRate rate);
    int update(ExchangeRate rate);
    int insertRevision(ExchangeRateRevision revision);
    int insertCreationRevision(ExchangeRateRevision revision);
    List<ExchangeRateRevision> selectRevisions(Long rateId);
}
