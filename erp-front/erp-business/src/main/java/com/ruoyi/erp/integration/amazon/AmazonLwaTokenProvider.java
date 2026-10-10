package com.ruoyi.erp.integration.amazon;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.ruoyi.erp.integration.amazon.AmazonAuthorizationException.Reason;

/** 补齐 SDK 1.13.0 未开放的 LWA HTTP 超时配置及并发刷新互斥。 */
final class AmazonLwaTokenProvider
{
    private static final long REFRESH_SKEW_SECONDS = 60;
    private static final long MAX_RESPONSE_BYTES = 16 * 1024;

    private final OkHttpClient httpClient;
    private final AmazonSpApiProperties properties;
    private final Clock clock;
    private String accessToken;
    private Instant refreshAt = Instant.MIN;

    AmazonLwaTokenProvider(OkHttpClient httpClient, AmazonSpApiProperties properties, Clock clock)
    {
        this.httpClient = httpClient;
        this.properties = properties;
        this.clock = clock;
    }

    synchronized String getAccessToken()
    {
        if (accessToken != null && clock.instant().isBefore(refreshAt))
        {
            return accessToken;
        }
        // 用请求开始时间计算有效期，避免将网络耗时算入令牌剩余寿命。
        Instant requestedAt = clock.instant();
        FormBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", properties.getClientId())
                .add("client_secret", properties.getClientSecret())
                .add("refresh_token", properties.getRefreshToken())
                .build();
        Request request = new Request.Builder().url(AmazonSpApiProperties.LWA_ENDPOINT)
                .header("User-Agent", "erp-v2/3.9.2 (Language=Java)").post(body).build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                Reason reason = response.code() == 429 ? Reason.THROTTLED
                        : response.code() >= 500 ? Reason.REMOTE_SERVICE : Reason.AUTHORIZATION;
                throw new AmazonAuthorizationException(reason, response.code());
            }
            try
            {
                ResponseBody responseBody = response.body();
                if (responseBody == null || responseBody.contentLength() > MAX_RESPONSE_BYTES)
                {
                    throw new IllegalArgumentException();
                }
                // 对 chunked 或未声明长度的响应也设置上限。
                String json = response.peekBody(MAX_RESPONSE_BYTES + 1).string();
                if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES)
                {
                    throw new IllegalArgumentException();
                }
                JsonObject token = JsonParser.parseString(json).getAsJsonObject();
                if (!token.get("access_token").isJsonPrimitive()
                        || !token.getAsJsonPrimitive("access_token").isString())
                {
                    throw new IllegalArgumentException();
                }
                String value = token.get("access_token").getAsString();
                long expiresIn = token.get("expires_in").getAsBigDecimal().longValueExact();
                if (value.isBlank() || value.length() > 2048 || value.chars().anyMatch(c -> c <= 32 || c >= 127)
                        || expiresIn <= 0)
                {
                    throw new IllegalArgumentException();
                }
                Instant expiresAt = requestedAt.plusSeconds(expiresIn);
                if (!clock.instant().isBefore(expiresAt))
                {
                    throw new IllegalArgumentException();
                }
                refreshAt = expiresAt.minusSeconds(Math.min(REFRESH_SKEW_SECONDS, expiresIn));
                accessToken = value;
                return value;
            }
            catch (RuntimeException ex)
            {
                throw new AmazonAuthorizationException(Reason.INVALID_RESPONSE, response.code());
            }
        }
        catch (IOException ex)
        {
            throw new AmazonAuthorizationException(Reason.NETWORK, 0);
        }
    }
}
