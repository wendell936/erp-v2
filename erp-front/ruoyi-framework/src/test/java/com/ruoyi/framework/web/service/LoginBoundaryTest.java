package com.ruoyi.framework.web.service;

import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.CaptchaExpireException;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 登录入口边界；审计异步调度、数据库和 Redis 均模拟，不代替真实登录验收。 */
class LoginBoundaryTest
{
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<MessageUtils> messages;
    private MockedStatic<AsyncFactory> audit;
    private MockedStatic<AsyncManager> async;
    private RedisCache cache;
    private AuthenticationManager authentication;
    private SysLoginService login;

    @BeforeEach
    void setUp()
    {
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean("scheduledExecutorService"))
                .thenReturn(mock(ScheduledExecutorService.class));
        messages = mockStatic(MessageUtils.class);
        audit = mockStatic(AsyncFactory.class);
        async = mockStatic(AsyncManager.class);
        AsyncManager manager = mock(AsyncManager.class);
        async.when(AsyncManager::me).thenReturn(manager);
        cache = mock(RedisCache.class);
        authentication = mock(AuthenticationManager.class);
        ISysConfigService config = mock(ISysConfigService.class);
        when(config.selectCaptchaEnabled()).thenReturn(true);
        login = new SysLoginService();
        ReflectionTestUtils.setField(login, "redisCache", cache);
        ReflectionTestUtils.setField(login, "configService", config);
        ReflectionTestUtils.setField(login, "authenticationManager", authentication);
    }

    @AfterEach
    void clearStaticMocks()
    {
        async.close();
        audit.close();
        messages.close();
        spring.close();
    }

    @Test
    void wrongCaptchaDoesNotReachPasswordAuthentication()
    {
        when(cache.getAndDeleteCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "test-id")).thenReturn("ABCD");
        assertThrows(CaptchaException.class, () -> login.login("ERP-TEST", "synthetic-password", "wrong", "test-id"));
        verifyNoInteractions(authentication);
        verify(cache).getAndDeleteCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "test-id");
        verifyNoMoreInteractions(cache);
    }

    @Test
    void missingCaptchaAnswerProducesBusinessErrorWithoutPasswordFailure()
    {
        when(cache.getAndDeleteCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "test-id")).thenReturn("ABCD");
        assertThrows(CaptchaException.class, () -> login.login("ERP-TEST", "synthetic-password", null, "test-id"));
        verifyNoInteractions(authentication);
    }

    @Test
    void expiredCaptchaDoesNotReachPasswordAuthentication()
    {
        assertThrows(CaptchaExpireException.class, () -> login.login("ERP-TEST", "synthetic-password", "ABCD", "test-id"));
        verifyNoInteractions(authentication);
        verify(cache, never()).deleteObject(anyString());
    }

    @Test
    void correctCaptchaIsCaseInsensitiveAndConsumedOnce()
    {
        when(cache.getAndDeleteCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "test-id")).thenReturn("ABCD", null);
        assertDoesNotThrow(() -> login.validateCaptcha("ERP-TEST", "abcd", "test-id"));
        assertThrows(CaptchaExpireException.class, () -> login.validateCaptcha("ERP-TEST", "abcd", "test-id"));
        verify(cache, times(2)).getAndDeleteCacheObject(CacheConstants.CAPTCHA_CODE_KEY + "test-id");
    }

    @Test
    void disabledOrDeletedAccountIsRejectedBeforePasswordValidation()
    {
        for (boolean disabled : new boolean[] {true, false})
        {
            ISysUserService users = mock(ISysUserService.class);
            SysPasswordService passwords = mock(SysPasswordService.class);
            SysUser user = new SysUser();
            user.setStatus(disabled ? "1" : "0");
            user.setDelFlag(disabled ? "0" : "2");
            when(users.selectUserByUserName("ERP-TEST")).thenReturn(user);
            UserDetailsServiceImpl details = new UserDetailsServiceImpl();
            ReflectionTestUtils.setField(details, "userService", users);
            ReflectionTestUtils.setField(details, "passwordService", passwords);
            assertThrows(ServiceException.class, () -> details.loadUserByUsername("ERP-TEST"));
            verifyNoInteractions(passwords);
        }
    }
}
