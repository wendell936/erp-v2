package com.ruoyi.erp.supplier.service;

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
import com.ruoyi.erp.supplier.domain.*;
import com.ruoyi.erp.supplier.mapper.SupplierMapper;

@Service
public class SupplierService
{
    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);
    private final SupplierMapper mapper;
    private final Validator validator;

    public SupplierService(SupplierMapper mapper, Validator validator)
    {
        this.mapper = mapper;
        this.validator = validator;
    }

    public List<Supplier> list(String supplierName, String status)
    {
        if (supplierName != null && supplierName.length() > 200)
        {
            throw new ServiceException("供应商名称筛选过长");
        }
        if (status != null && !status.isEmpty() && !status.matches("[01]"))
        {
            throw new ServiceException("状态仅允许0启用或1停用");
        }
        return database("list", null, () -> mapper.selectList(text(supplierName), status));
    }

    public Supplier get(Long id)
    {
        if (id == null || id <= 0)
        {
            throw new ServiceException("供应商编号无效");
        }
        Supplier supplier = database("get", id, () -> mapper.selectById(id));
        if (supplier == null)
        {
            throw new ServiceException("供应商不存在");
        }
        return supplier;
    }

    public List<SupplierChange> changes(Long id)
    {
        // 不先查询详情，以免在 PageHelper 分页窗口内消费错误的查询。
        if (id == null || id <= 0)
        {
            throw new ServiceException("供应商编号无效");
        }
        return database("changes", id, () -> mapper.selectChanges(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Supplier create(SupplierSaveRequest request)
    {
        normalize(request);
        validate(request, SupplierSaveRequest.Create.class);
        Supplier supplier = values(request);
        supplier.setRequestId(request.getRequestId().toLowerCase(Locale.ROOT));
        supplier.setCreateBy(SecurityUtils.getUsername());
        supplier.setCreateTime(Date.from(Instant.now()));
        supplier.setRequestHash(hash(supplier));
        database("create", null, () -> mapper.insertOrFind(supplier)); // 唯一索引串行化同一请求键。
        Supplier saved = get(supplier.getSupplierId());
        if (!supplier.getRequestHash().equals(saved.getRequestHash()))
        {
            throw new ServiceException("创建请求标识已用于其他内容，请核对后使用新的标识");
        }
        database("create-audit", saved.getSupplierId(), () -> mapper.insertCreationChange(
                change(saved.getSupplierId(), 0L, "CREATE", "创建供应商", saved.getCreateBy(), saved.getCreateTime())));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Supplier update(Long id, SupplierSaveRequest request)
    {
        normalize(request);
        validate(request, SupplierSaveRequest.Update.class);
        Supplier supplier = values(request);
        supplier.setSupplierId(id);
        get(id);
        supplier.setVersion(request.getVersion());
        supplier.setUpdateBy(SecurityUtils.getUsername());
        supplier.setUpdateTime(Date.from(Instant.now()));
        if (database("update", id, () -> mapper.update(supplier)) != 1)
        {
            throw new ServiceException("供应商已被其他操作修改，请刷新后重试");
        }
        database("update-audit", id, () -> mapper.insertChange(change(id, request.getVersion() + 1,
                "UPDATE", request.getReason(), supplier.getUpdateBy(), supplier.getUpdateTime())));
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Supplier status(Long id, SupplierStatusRequest request)
    {
        request.setReason(text(request.getReason()));
        validate(request, jakarta.validation.groups.Default.class);
        Supplier supplier = get(id);
        supplier.setStatus(request.getStatus());
        supplier.setVersion(request.getVersion());
        supplier.setUpdateBy(SecurityUtils.getUsername());
        supplier.setUpdateTime(Date.from(Instant.now()));
        if (database("status", id, () -> mapper.updateStatus(supplier)) != 1)
        {
            throw new ServiceException("供应商已被其他操作修改，请刷新后重试");
        }
        database("status-audit", id, () -> mapper.insertChange(change(id, request.getVersion() + 1,
                "STATUS", request.getReason(), supplier.getUpdateBy(), supplier.getUpdateTime())));
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
            // 保留可定位的操作/主键/错误类型；SQL异常消息可能含联系人等参数，不传递或输出。
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
            log.error("supplier database failure: operation={}, supplierId={}, type={}, sqlState={}, vendorCode={}",
                    operation, id, exception.getClass().getSimpleName(), sqlState, vendorCode);
            throw new ServiceException("供应商数据操作失败，请稍后重试或联系管理员");
        }
    }

    private void validate(Object request, Class<?> group)
    {
        if (!validator.validate(request, group).isEmpty())
        {
            // 不把联系方式等被拒绝的字段值交给通用异常日志。
            throw new ServiceException("供应商资料不符合要求，请核对必填项、长度、版本和请求标识");
        }
    }

    private void normalize(SupplierSaveRequest request)
    {
        request.setSupplierName(text(request.getSupplierName()));
        request.setContactName(text(request.getContactName()));
        request.setContactPhone(text(request.getContactPhone()));
        request.setSettlementTerms(text(request.getSettlementTerms()));
        request.setRemark(text(request.getRemark()));
        request.setReason(text(request.getReason()));
    }

    private String text(String value)
    {
        return value == null ? "" : value.strip();
    }

    private Supplier values(SupplierSaveRequest request)
    {
        Supplier supplier = new Supplier();
        supplier.setSupplierName(request.getSupplierName());
        supplier.setContactName(request.getContactName());
        supplier.setContactPhone(request.getContactPhone());
        supplier.setSettlementTerms(request.getSettlementTerms());
        supplier.setRemark(request.getRemark());
        return supplier;
    }

    private String hash(Supplier supplier)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(supplier.getSupplierName(), supplier.getContactName(),
                    supplier.getContactPhone(), supplier.getSettlementTerms(), supplier.getRemark(), supplier.getCreateBy()))
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

    private SupplierChange change(Long id, Long version, String operation, String reason, String operator, Date time)
    {
        SupplierChange change = new SupplierChange();
        change.setSupplierId(id);
        change.setVersion(version);
        change.setOperation(operation);
        change.setReason(reason);
        change.setOperator(operator);
        change.setChangeTime(time);
        return change;
    }
}
