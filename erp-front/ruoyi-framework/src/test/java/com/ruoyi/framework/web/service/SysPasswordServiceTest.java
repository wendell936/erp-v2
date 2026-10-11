package com.ruoyi.framework.web.service;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.common.exception.user.UserPasswordRetryLimitExceedException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysPasswordServiceTest
{
    private static final String USERNAME = "erp-test-user";
    private static final String PASSWORD = "synthetic-test-password";
    private static final String CACHE_KEY = CacheConstants.PWD_ERR_CNT_KEY + USERNAME;
    private static String encodedPassword;

    private RedisCache redisCache;
    private SysPasswordService passwordService;
    private SysUser user;

    @BeforeAll
    static void encodeTestPassword()
    {
        encodedPassword = SecurityUtils.encryptPassword(PASSWORD);
    }

    @BeforeEach
    void setUp()
    {
        redisCache = mock(RedisCache.class);
        passwordService = new SysPasswordService();
        ReflectionTestUtils.setField(passwordService, "redisCache", redisCache);
        ReflectionTestUtils.setField(passwordService, "maxRetryCount", 5);
        ReflectionTestUtils.setField(passwordService, "lockTime", 10);
        user = new SysUser();
        user.setUserName(USERNAME);
        user.setPassword(encodedPassword);
        when(redisCache.deleteBelowLimit(CACHE_KEY, 5)).thenReturn(true);
    }

    @AfterEach
    void clearAuthenticationContext()
    {
        AuthenticationContextHolder.clearContext();
    }

    @Test
    void firstIncorrectPasswordStartsFailureWindow()
    {
        authenticateWith("incorrect-password");

        assertThrows(UserPasswordNotMatchException.class, () -> passwordService.validate(user));

        verify(redisCache).incrementWithLimit(CACHE_KEY, 5, 10, TimeUnit.MINUTES);
        verify(redisCache, never()).deleteObject(CACHE_KEY);
    }

    @Test
    void fifthIncorrectPasswordReachesLockThreshold()
    {
        when(redisCache.<Integer>getCacheObject(CACHE_KEY)).thenReturn(4);
        authenticateWith("incorrect-password");

        assertThrows(UserPasswordNotMatchException.class, () -> passwordService.validate(user));

        verify(redisCache).incrementWithLimit(CACHE_KEY, 5, 10, TimeUnit.MINUTES);
    }

    @Test
    void lockedAccountRejectsEvenCorrectPassword()
    {
        when(redisCache.<Integer>getCacheObject(CACHE_KEY)).thenReturn(5);
        authenticateWith(PASSWORD);

        assertThrows(UserPasswordRetryLimitExceedException.class, () -> passwordService.validate(user));

        verify(redisCache, never()).deleteObject(CACHE_KEY);
        verify(redisCache, never()).incrementWithLimit(CACHE_KEY, 5, 10, TimeUnit.MINUTES);
        verify(redisCache, never()).deleteBelowLimit(CACHE_KEY, 5);
    }

    @Test
    void successfulPasswordBeforeLockClearsFailureCount()
    {
        when(redisCache.<Integer>getCacheObject(CACHE_KEY)).thenReturn(4);
        authenticateWith(PASSWORD);

        assertDoesNotThrow(() -> passwordService.validate(user));

        verify(redisCache).deleteBelowLimit(CACHE_KEY, 5);
    }

    @Test
    void accountIdentityUsesStoredNameInsteadOfInputCase()
    {
        AuthenticationContextHolder.setContext(new UsernamePasswordAuthenticationToken(
                USERNAME.toUpperCase(), "incorrect-password"));
        assertThrows(UserPasswordNotMatchException.class, () -> passwordService.validate(user));
        verify(redisCache).incrementWithLimit(CACHE_KEY, 5, 10, TimeUnit.MINUTES);
    }

    @Test
    void concurrentLockCannotBeClearedBySuccessfulPassword()
    {
        when(redisCache.<Integer>getCacheObject(CACHE_KEY)).thenReturn(4);
        when(redisCache.deleteBelowLimit(CACHE_KEY, 5)).thenReturn(false);
        authenticateWith(PASSWORD);
        assertThrows(UserPasswordRetryLimitExceedException.class, () -> passwordService.validate(user));
        verify(redisCache, never()).deleteObject(CACHE_KEY);
    }

    @Test
    void expiredFailureWindowAllowsCorrectPassword()
    {
        when(redisCache.<Integer>getCacheObject(CACHE_KEY)).thenReturn(null);
        authenticateWith(PASSWORD);

        assertDoesNotThrow(() -> passwordService.validate(user));

        verify(redisCache).deleteBelowLimit(CACHE_KEY, 5);
    }

    private void authenticateWith(String password)
    {
        AuthenticationContextHolder.setContext(
                new UsernamePasswordAuthenticationToken(USERNAME, password));
    }
}
