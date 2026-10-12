package com.ruoyi.erp.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.xss.XssValidator;
import com.ruoyi.erp.finance.domain.*;
import com.ruoyi.erp.finance.mapper.ExchangeRateMapper;

@Service
public class ExchangeRateService
{
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private final ExchangeRateMapper mapper;

    public ExchangeRateService(ExchangeRateMapper mapper)
    {
        this.mapper = mapper;
    }

    public List<ExchangeRate> list(String from, String to)
    {
        LocalDate start = optionalDate(from);
        LocalDate end = optionalDate(to);
        if (start != null && end != null && start.isAfter(end))
        {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        return database("list", null, () -> mapper.selectList(start, end));
    }

    public ExchangeRate get(Long id)
    {
        validId(id);
        ExchangeRate rate = database("get", id, () -> mapper.selectById(id));
        if (rate == null) throw new ServiceException("汇率记录不存在");
        return rate;
    }

    public List<ExchangeRateRevision> revisions(Long id)
    {
        validId(id);
        return database("revisions", id, () -> mapper.selectRevisions(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ExchangeRate create(ExchangeRateRequest request)
    {
        if (request == null) throw new ServiceException("请填写汇率资料");
        ExchangeRate rate = new ExchangeRate();
        rate.setRateDate(date(request.rateDate()));
        rate.setUsdCny(rate(request.usdCny()));
        String requestId = text(request.requestId()).toLowerCase(Locale.ROOT);
        if (!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        {
            throw new ServiceException("新增汇率需要有效的请求标识");
        }
        rate.setRequestId(requestId);
        rate.setCreateBy(SecurityUtils.getUsername());
        rate.setCreateTime(new Date());
        rate.setRequestHash(hash(rate));
        database("create", null, () -> mapper.insertOrFind(rate));
        ExchangeRate saved = get(rate.getRateId());
        if (!requestId.equals(saved.getRequestId())) throw new ServiceException("该日期已有汇率，请打开现有记录修改");
        if (!rate.getRequestHash().equals(saved.getRequestHash()))
        {
            throw new ServiceException("请求标识已用于其他内容，请核对后重新新增");
        }
        // 原始创建审计仅在第一次提交写入，后来编辑后重试不能改写历史版本。
        ExchangeRateRevision creation = revision(rate, 0L, "新增日期汇率", saved.getCreateBy(), saved.getCreateTime());
        database("create-revision", saved.getRateId(), () -> mapper.insertCreationRevision(creation));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public ExchangeRate update(Long id, ExchangeRateRequest request)
    {
        if (request == null || request.version() == null || request.version() < 0 || request.version() == Long.MAX_VALUE)
        {
            throw new ServiceException("请提供有效的当前版本");
        }
        ExchangeRate current = get(id);
        if (!current.getRateDate().equals(date(request.rateDate())))
        {
            throw new ServiceException("汇率日期不可修改；请为其他日期新增记录");
        }
        String reason = text(request.reason());
        if (reason.isEmpty() || reason.length() > 500 || XssValidator.containsHtml(reason))
        {
            throw new ServiceException("请填写不超过500字的纯文本修改原因");
        }
        current.setUsdCny(rate(request.usdCny()));
        current.setVersion(request.version());
        current.setUpdateBy(SecurityUtils.getUsername());
        current.setUpdateTime(new Date());
        if (database("update", id, () -> mapper.update(current)) != 1)
        {
            throw new ServiceException("汇率已被其他操作修改，请刷新后核对再试");
        }
        database("update-revision", id, () -> mapper.insertRevision(revision(current, request.version() + 1,
                reason, current.getUpdateBy(), current.getUpdateTime())));
        return get(id);
    }

    /** 服务端业务确认可调用此方法，然后在同一业务事务保存所有值；预览不代表确认。 */
    public CurrencySnapshot snapshot(ConversionRequest request)
    {
        if (request == null || !("CNY".equals(request.currency()) || "USD".equals(request.currency())))
        {
            throw new ServiceException("币种仅支持CNY或USD");
        }
        String input = text(request.originalAmount());
        if (!input.matches("-?[0-9]{1,12}(\\.[0-9]{1,2})?"))
        {
            throw new ServiceException("原币金额最多12位整数和2位小数，请输入普通十进制数");
        }
        BigDecimal amount = new BigDecimal(input).setScale(2, RoundingMode.UNNECESSARY);
        if ("CNY".equals(request.currency()))
        {
            // 人民币原币无需USD汇率，也不伪造汇率日期或外部记录。
            return new CurrencySnapshot("CNY", amount, null, null, null, BigDecimal.ONE.setScale(8), amount);
        }
        LocalDate rateDate = date(request.rateDate());
        ExchangeRate rate = database("snapshot", null, () -> mapper.selectByDate(rateDate));
        if (rate == null) throw new ServiceException("缺少" + rateDate + "的USD兑CNY汇率，请先补充当日汇率");
        return new CurrencySnapshot("USD", amount, rateDate, rate.getRateId(), rate.getVersion(), rate.getUsdCny(),
                amount.multiply(rate.getUsdCny()).setScale(2, RoundingMode.HALF_UP));
    }

    private LocalDate optionalDate(String input)
    {
        return text(input).isEmpty() ? null : date(input);
    }

    private LocalDate date(String input)
    {
        String value = text(input);
        try
        {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", value, 0);
            LocalDate result = LocalDate.parse(value);
            if (result.getYear() < 1000) throw new DateTimeParseException("range", value, 0);
            return result;
        }
        catch (DateTimeParseException exception)
        {
            throw new ServiceException("汇率日期必须为有效的yyyy-MM-dd日期（1000至9999年）");
        }
    }

    private BigDecimal rate(String input)
    {
        String value = text(input);
        if (!value.matches("[0-9]{1,8}(\\.[0-9]{1,8})?"))
        {
            throw new ServiceException("汇率必须为正十进制数，最多8位整数和8位小数");
        }
        BigDecimal result = new BigDecimal(value);
        if (result.signum() <= 0) throw new ServiceException("汇率必须大于0");
        return result.setScale(8, RoundingMode.UNNECESSARY);
    }

    private void validId(Long id)
    {
        if (id == null || id <= 0) throw new ServiceException("汇率编号无效");
    }

    private String text(String value)
    {
        return value == null ? "" : value.strip();
    }

    private String hash(ExchangeRate rate)
    {
        try
        {
            String value = rate.getRateDate() + "\n" + rate.getUsdCny().toPlainString() + "\n" + rate.getCreateBy();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ExchangeRateRevision revision(ExchangeRate rate, Long version, String reason, String operator, Date time)
    {
        ExchangeRateRevision result = new ExchangeRateRevision();
        result.setRateId(rate.getRateId());
        result.setRateDate(rate.getRateDate());
        result.setUsdCny(rate.getUsdCny());
        result.setVersion(version);
        result.setReason(reason);
        result.setOperator(operator);
        result.setChangeTime(time);
        return result;
    }

    private <T> T database(String operation, Long id, java.util.function.Supplier<T> action)
    {
        try
        {
            return action.get();
        }
        catch (DataAccessException exception)
        {
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
            log.error("exchange rate database failure: operation={}, rateId={}, type={}, sqlState={}, vendorCode={}",
                    operation, id, exception.getClass().getSimpleName(), sqlState, vendorCode);
            throw new ServiceException("汇率数据操作失败，请稍后重试或联系管理员");
        }
    }
}
