package com.ruoyi.erp.integration.amazon;

import java.time.Clock;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.spapi.ApiClient;
import software.amazon.spapi.api.sellers.v1.SellersApi;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AmazonSpApiProperties.class)
@ConditionalOnProperty(prefix = "erp.amazon.sp-api", name = "enabled", havingValue = "true")
public class AmazonSpApiConfiguration
{
    @Bean("amazonSpApiClient")
    public ApiClient amazonSpApiClient(AmazonSpApiProperties properties)
    {
        properties.validateEnabledConfiguration();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .writeTimeout(properties.getReadTimeout())
                .callTimeout(properties.getCallTimeout())
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();
        AmazonLwaTokenProvider tokenProvider = new AmazonLwaTokenProvider(httpClient, properties, Clock.systemUTC());
        return new ApiClient(httpClient)
                .setBasePath(AmazonSpApiProperties.API_ENDPOINT)
                .setUserAgent("erp-v2/3.9.2 (Language=Java)")
                .setLWAAuthorizationSigner(new AmazonLwaAuthorizationSigner(properties, tokenProvider));
    }

    @Bean
    public SellersApi amazonSellersApi(@Qualifier("amazonSpApiClient") ApiClient apiClient)
    {
        // 保留官方按操作限流。客户端为单例，避免新建实例重置限流计数。
        return new SellersApi(apiClient, false);
    }
}
