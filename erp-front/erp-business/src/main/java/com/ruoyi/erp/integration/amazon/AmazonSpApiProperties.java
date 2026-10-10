package com.ruoyi.erp.integration.amazon;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 单美国站对接配置。不要将配置对象或凭据写入日志。 */
@ConfigurationProperties(prefix = "erp.amazon.sp-api")
public class AmazonSpApiProperties
{
    public static final String API_ENDPOINT = "https://sellingpartnerapi-na.amazon.com";
    public static final String LWA_ENDPOINT = "https://api.amazon.com/auth/o2/token";

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private Duration callTimeout = Duration.ofSeconds(45);

    public void validateEnabledConfiguration()
    {
        requireText(clientId, "client-id");
        requireText(clientSecret, "client-secret");
        requireText(refreshToken, "refresh-token");
        requireTimeout(connectTimeout, "connect-timeout");
        requireTimeout(readTimeout, "read-timeout");
        requireTimeout(callTimeout, "call-timeout");
    }

    private void requireText(String value, String key)
    {
        if (!StringUtils.hasText(value))
        {
            throw new IllegalStateException("Amazon SP-API 缺少配置：" + key);
        }
    }

    private void requireTimeout(Duration value, String key)
    {
        if (value == null || value.compareTo(Duration.ofMillis(1)) < 0
                || value.compareTo(Duration.ofMinutes(2)) > 0)
        {
            throw new IllegalStateException("Amazon SP-API 超时配置须为 1ms 至 2m：" + key);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getCallTimeout() { return callTimeout; }
    public void setCallTimeout(Duration callTimeout) { this.callTimeout = callTimeout; }

    @Override
    public String toString()
    {
        return "AmazonSpApiProperties[enabled=" + enabled + ", credentials=REDACTED]";
    }
}
