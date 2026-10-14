package com.ruoyi.erp.catalog.service;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.erp.catalog.domain.ProductType;
import com.ruoyi.erp.catalog.domain.SkuNumber;
import com.ruoyi.erp.catalog.mapper.SkuNumberMapper;

/** 内部编码基础；不创建商品、Listing、库存或成本事实，无独立 HTTP 分配接口。 */
@Service
public class SkuNumberService
{
    private static final Logger log = LoggerFactory.getLogger(SkuNumberService.class);
    private final SkuNumberMapper mapper;

    public SkuNumberService(SkuNumberMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public SkuNumber allocateMain(Long typeId, String requestId)
    {
        return allocate(typeId, null, requestId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public SkuNumber allocateVariant(Long mainNumberId, String requestId)
    {
        if (mainNumberId == null || mainNumberId <= 0)
        {
            throw new ServiceException("主编码记录编号无效");
        }
        try
        {
            SkuNumber main = mapper.selectById(mainNumberId);
            if (main == null || main.getMainNumberId() != null)
            {
                throw new ServiceException("只能在已存在的主编码下分配变体编号");
            }
            return allocate(main.getTypeId(), mainNumberId, requestId);
        }
        catch (DataAccessException exception)
        {
            throw failure(exception);
        }
    }

    private SkuNumber allocate(Long typeId, Long mainNumberId, String requestId)
    {
        // REQUIRED 加入外层事务时，注解不能覆盖外层隔离级别。
        // 两次请求键查询必须看见等待类型锁期间已提交的编号，避免 RR 旧快照破坏重试语义。
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
        {
            throw new ServiceException("编号分配必须加入 READ_COMMITTED 业务事务");
        }
        if (typeId == null || typeId <= 0 || requestId == null
                || !requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        {
            throw new ServiceException("编码分配需要有效类型编号及 UUID 请求标识");
        }
        String key = requestId.toLowerCase(Locale.ROOT);
        String operator = SecurityUtils.getUsername();
        try
        {
            SkuNumber saved = mapper.selectByRequest(key);
            if (saved != null) return retry(saved, typeId, mainNumberId, operator);

            // 统一先锁类型、再锁主编码；与类型编辑/停用串行化，不用 MAX+1。
            ProductType type = mapper.lockType(typeId);
            if (type == null) throw new ServiceException("产品类型不存在");
            saved = mapper.selectByRequest(key);
            if (saved != null) return retry(saved, typeId, mainNumberId, operator);
            if (!"0".equals(type.getStatus())) throw new ServiceException("停用类型不能分配新编码");
            if (type.getTypeCode() == null || !type.getTypeCode().matches("[A-Za-z0-9]+"))
                throw new ServiceException("类型码仅允许英文字母和数字，请先核对资料及数据库升级状态");

            SkuNumber number = new SkuNumber();
            number.setTypeId(typeId);
            number.setMainNumberId(mainNumberId);
            long sequence;
            if (mainNumberId == null)
            {
                sequence = next(type.getLastNumber());
                if (mapper.advanceMain(typeId, type.getLastNumber()) != 1)
                    throw new ServiceException("编号计数已变化，请重新核对");
                number.setSkuCode(type.getTypeCode() + "-" + sequence);
            }
            else
            {
                SkuNumber main = mapper.lockNumber(mainNumberId);
                if (main == null || main.getMainNumberId() != null || !typeId.equals(main.getTypeId()))
                    throw new ServiceException("主编码记录无效");
                sequence = next(main.getLastVariantNumber());
                if (mapper.advanceVariant(mainNumberId, main.getLastVariantNumber()) != 1)
                    throw new ServiceException("变体计数已变化，请重新核对");
                number.setSkuCode(main.getSkuCode() + "-" + sequence);
            }
            number.setSequenceNumber(sequence);
            number.setLastVariantNumber(0L);
            number.setRequestId(key);
            number.setCreateBy(operator);
            number.setCreateTime(Date.from(Instant.now()));
            mapper.insert(number);
            return number;
        }
        catch (DataAccessException exception)
        {
            throw failure(exception);
        }
    }

    private SkuNumber retry(SkuNumber saved, Long typeId, Long mainNumberId, String operator)
    {
        if (!typeId.equals(saved.getTypeId()) || !Objects.equals(mainNumberId, saved.getMainNumberId())
                || !operator.equals(saved.getCreateBy()))
            throw new ServiceException("编号请求标识已用于其他范围或操作人");
        return saved;
    }

    private long next(Long last)
    {
        if (last == null || last < 0 || last == Long.MAX_VALUE)
            throw new ServiceException("编号计数无效或已达存储上限，请联系管理员");
        return last + 1;
    }

    private ServiceException failure(DataAccessException exception)
    {
        log.error("sku number allocation failed: type={}", exception.getClass().getSimpleName());
        return new ServiceException("编码分配未完成，可能存在类型码冲突或数据操作失败；请核对后使用原请求标识重试");
    }
}
