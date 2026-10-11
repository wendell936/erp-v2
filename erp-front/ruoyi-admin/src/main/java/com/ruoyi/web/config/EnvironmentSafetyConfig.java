package com.ruoyi.web.config;

import java.util.Base64;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** 在创建连接池和认证服务前检查凭据，仅报告配置名，不输出配置值。 */
@Configuration(proxyBeanMethods = false)
public class EnvironmentSafetyConfig
{
    @Bean
    public static BeanFactoryPostProcessor requiredEnvironmentCredentials(Environment environment)
    {
        return beanFactory -> {
            requireText(environment, "spring.datasource.druid.master.url");
            requireText(environment, "spring.datasource.druid.master.username");
            requireText(environment, "spring.datasource.druid.master.password");
            String tokenSecret = requireText(environment, "token.secret");
            try
            {
                if (Base64.getDecoder().decode(tokenSecret).length < 64)
                {
                    throw new IllegalArgumentException();
                }
            }
            catch (IllegalArgumentException exception)
            {
                throw new IllegalStateException("token.secret 须为至少 64 字节随机密钥的 Base64 编码");
            }
            if (environment.getProperty("spring.datasource.druid.stat-view-servlet.enabled", Boolean.class, false))
            {
                requireText(environment, "spring.datasource.druid.stat-view-servlet.login-username");
                requireText(environment, "spring.datasource.druid.stat-view-servlet.login-password");
            }
        };
    }

    private static String requireText(Environment environment, String key)
    {
        String value = environment.getProperty(key);
        if (!StringUtils.hasText(value))
        {
            throw new IllegalStateException("缺少环境配置：" + key);
        }
        return value;
    }
}
