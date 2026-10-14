package com.ruoyi.erp.catalog.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.erp.catalog.domain.*;
import com.ruoyi.erp.catalog.mapper.ProductTypeMapper;

@Service
public class ProductTypeService
{
    private static final Logger log = LoggerFactory.getLogger(ProductTypeService.class);
    private final ProductTypeMapper mapper;
    private final Validator validator;

    public ProductTypeService(ProductTypeMapper mapper, Validator validator)
    {
        this.mapper = mapper;
        this.validator = validator;
    }

    public List<ProductType> list(String typeName, String status)
    {
        if (typeName != null && typeName.length() > 200)
        {
            throw new ServiceException("产品类型名称筛选过长");
        }
        if (status != null && !status.isEmpty() && !status.matches("[01]"))
        {
            throw new ServiceException("状态仅允许0启用或1停用");
        }
        return database("list", null, () -> mapper.selectList(text(typeName), status));
    }

    public ProductType get(Long id)
    {
        if (id == null || id <= 0)
        {
            throw new ServiceException("产品类型编号无效");
        }
        ProductType productType = database("get", id, () -> mapper.selectById(id));
        if (productType == null)
        {
            throw new ServiceException("产品类型不存在");
        }
        return productType;
    }

    public List<ProductTypeChange> changes(Long id)
    {
        // 不先查询详情，以免在 PageHelper 分页窗口内消费错误的查询。
        if (id == null || id <= 0)
        {
            throw new ServiceException("产品类型编号无效");
        }
        return database("changes", id, () -> mapper.selectChanges(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductType create(ProductTypeSaveRequest request)
    {
        normalize(request);
        validate(request, ProductTypeSaveRequest.Create.class);
        ProductType productType = values(request);
        productType.setRequestId(request.getRequestId().toLowerCase(Locale.ROOT));
        productType.setCreateBy(SecurityUtils.getUsername());
        productType.setCreateTime(Date.from(Instant.now()));
        productType.setRequestHash(hash(productType));
        database("create", null, () -> mapper.insertOrFind(productType)); // 唯一索引串行化同一请求键。
        ProductType saved = get(productType.getTypeId());
        if (!productType.getRequestId().equals(saved.getRequestId())
                || !productType.getRequestHash().equals(saved.getRequestHash()))
        {
            throw new ServiceException("类型码已存在或创建请求标识已用于其他内容，请核对后重试");
        }
        database("create-audit", saved.getTypeId(), () -> mapper.insertCreationChange(
                change(saved.getTypeId(), 0L, "CREATE", "创建产品类型", saved.getCreateBy(), saved.getCreateTime())));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductType update(Long id, ProductTypeSaveRequest request)
    {
        normalize(request);
        validate(request, ProductTypeSaveRequest.Update.class);
        ProductType productType = values(request);
        productType.setTypeId(id);
        get(id);
        productType.setVersion(request.getVersion());
        productType.setUpdateBy(SecurityUtils.getUsername());
        productType.setUpdateTime(Date.from(Instant.now()));
        if (database("update", id, () -> mapper.update(productType)) != 1)
        {
            throw new ServiceException("产品类型已变化，或已分配编号后不能修改类型码，请刷新核对");
        }
        database("update-audit", id, () -> mapper.insertChange(change(id, request.getVersion() + 1,
                "UPDATE", request.getReason(), productType.getUpdateBy(), productType.getUpdateTime())));
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductType status(Long id, ProductTypeStatusRequest request)
    {
        request.setReason(text(request.getReason()));
        validate(request, jakarta.validation.groups.Default.class);
        ProductType productType = get(id);
        productType.setStatus(request.getStatus());
        productType.setVersion(request.getVersion());
        productType.setUpdateBy(SecurityUtils.getUsername());
        productType.setUpdateTime(Date.from(Instant.now()));
        if (database("status", id, () -> mapper.updateStatus(productType)) != 1)
        {
            throw new ServiceException("产品类型已变化，请刷新核对后再操作");
        }
        database("status-audit", id, () -> mapper.insertChange(change(id, request.getVersion() + 1,
                "STATUS", request.getReason(), productType.getUpdateBy(), productType.getUpdateTime())));
        return get(id);
    }

    private <T> T database(String operation, Long id, java.util.function.Supplier<T> action)
    {
        try
        {
            return action.get();
        }
        catch (DataAccessException exception)
        {
            // 不输出 SQL 消息或绑定参数，防止资料及数据库结构进入日志。
            String sqlState = "unknown";
            int vendorCode = 0;
            for (Throwable cause = exception; cause != null; cause = cause.getCause())
            {
                if (cause instanceof java.sql.SQLException sql)
                {
                    sqlState = sql.getSQLState();
                    vendorCode = sql.getErrorCode();
                    break;
                }
            }
            log.error("productType database failure: operation={}, typeId={}, type={}, sqlState={}, vendorCode={}",
                    operation, id, exception.getClass().getSimpleName(), sqlState, vendorCode);
            throw new ServiceException("产品类型数据操作失败，请稍后重试或联系管理员");
        }
    }

    private void validate(Object request, Class<?> group)
    {
        if (!validator.validate(request, group).isEmpty())
        {
            // 不将被拒绝的原始字段交给通用异常日志。
            throw new ServiceException("产品类型资料不符合要求，请核对必填项、长度、版本和请求标识");
        }
    }

    private void normalize(ProductTypeSaveRequest request)
    {
        request.setTypeName(text(request.getTypeName()));
        request.setTypeCode(text(request.getTypeCode()));
        request.setKeywords(text(request.getKeywords()));
        request.setRemark(text(request.getRemark()));
        request.setReason(text(request.getReason()));
    }

    private String text(String value)
    {
        return value == null ? "" : value.strip();
    }

    private ProductType values(ProductTypeSaveRequest request)
    {
        ProductType productType = new ProductType();
        productType.setTypeName(request.getTypeName());
        productType.setTypeCode(request.getTypeCode());
        productType.setKeywords(request.getKeywords());
        productType.setRemark(request.getRemark());
        return productType;
    }

    private String hash(ProductType productType)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(productType.getTypeName(), productType.getTypeCode(),
                    productType.getKeywords(), productType.getRemark(), productType.getCreateBy()))
            {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (java.security.NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ProductTypeChange change(Long id, Long version, String operation, String reason, String operator, Date time)
    {
        ProductTypeChange change = new ProductTypeChange();
        change.setTypeId(id);
        change.setVersion(version);
        change.setOperation(operation);
        change.setReason(reason);
        change.setOperator(operator);
        change.setChangeTime(time);
        return change;
    }
}
