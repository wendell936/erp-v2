package com.ruoyi.erp.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.erp.finance.domain.*;
import com.ruoyi.erp.finance.mapper.ExchangeRateMapper;
import com.ruoyi.erp.finance.service.ExchangeRateService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeRateServiceTest
{
    private final ExchangeRateMapper mapper = mock(ExchangeRateMapper.class);
    private final ExchangeRateService service = new ExchangeRateService(mapper);

    @Test
    void decimalHalfUpWorksForPositiveNegativeAndLargeAmounts()
    {
        rate("7.50000000");
        assertEquals(new BigDecimal("0.08"), usd("0.01").cnyAmount());
        assertEquals(new BigDecimal("-0.08"), usd("-0.01").cnyAmount());
        rate("99999999.99999999");
        BigDecimal expected = new BigDecimal("999999999999.99").multiply(new BigDecimal("99999999.99999999"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, usd("999999999999.99").cnyAmount());
        rate("0.00000001");
        assertEquals(new BigDecimal("0.00"), usd("0.01").cnyAmount());
    }

    @Test
    void snapshotsAreIndependentOfLaterMutableRates()
    {
        ExchangeRate rate = rate("7.25000000");
        CurrencySnapshot old = usd("100");
        rate.setUsdCny(new BigDecimal("8.00000000"));
        rate.setVersion(1L);
        CurrencySnapshot latest = usd("100");
        assertEquals(new BigDecimal("725.00"), old.cnyAmount());
        assertEquals(new BigDecimal("7.25000000"), old.usdCny());
        assertEquals(0L, old.rateVersion());
        assertEquals(new BigDecimal("800.00"), latest.cnyAmount());
        assertEquals(1L, latest.rateVersion());
    }

    @Test
    void cnyDoesNotNeedOrInventAnExchangeRate()
    {
        CurrencySnapshot result = service.snapshot(new ConversionRequest("CNY", "-1.23", null));
        assertEquals(new BigDecimal("-1.23"), result.cnyAmount());
        assertNull(result.rateDate());
        assertNull(result.rateId());
        assertNull(result.rateVersion());
        verifyNoInteractions(mapper);
    }

    @Test
    void missingExactDateIsAnErrorAndNeverFallsBack()
    {
        ServiceException error = assertThrows(ServiceException.class, () -> usd("0"));
        assertTrue(error.getMessage().contains("2026-10-11"));
        verify(mapper).selectByDate(LocalDate.of(2026, 10, 11));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.000000001", "100000000", "1e-8", "NaN", "", ".5"})
    void invalidRatesAreRejectedBeforeDatabaseAccess(String value)
    {
        assertThrows(ServiceException.class, () -> service.create(
                new ExchangeRateRequest("2026-10-11", value, UUID.randomUUID().toString(), null, null)));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-29", "2026-13-01", "2026-10-11T00:00:00Z", "2026-1-1", "0999-01-01", ""})
    void invalidDatesCannotBeConvertedOrShifted(String date)
    {
        assertThrows(ServiceException.class, () -> service.snapshot(new ConversionRequest("USD", "1.00", date)));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "1000000000000", "1e2", "NaN", "Infinity", "", ".5"})
    void amountsCannotSilentlyRoundOrOverflow(String amount)
    {
        assertThrows(ServiceException.class, () -> usd(amount));
        verifyNoInteractions(mapper);
    }

    @Test
    void unsupportedCurrencyAndReversedDateRangesAreRejected()
    {
        assertThrows(ServiceException.class, () -> service.snapshot(new ConversionRequest("EUR", "1", "2026-10-11")));
        assertThrows(ServiceException.class, () -> service.list("2026-10-12", "2026-10-11"));
        verifyNoInteractions(mapper);
    }

    private CurrencySnapshot usd(String amount)
    {
        return service.snapshot(new ConversionRequest("USD", amount, "2026-10-11"));
    }

    private ExchangeRate rate(String value)
    {
        ExchangeRate rate = new ExchangeRate();
        rate.setRateId(5L);
        rate.setRateDate(LocalDate.of(2026, 10, 11));
        rate.setVersion(0L);
        rate.setUsdCny(new BigDecimal(value));
        when(mapper.selectByDate(rate.getRateDate())).thenReturn(rate);
        return rate;
    }
}
