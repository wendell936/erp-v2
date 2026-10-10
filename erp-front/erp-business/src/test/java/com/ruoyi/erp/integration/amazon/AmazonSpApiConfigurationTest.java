package com.ruoyi.erp.integration.amazon;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.spapi.ApiClient;
import software.amazon.spapi.api.sellers.v1.SellersApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AmazonSpApiConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AmazonSpApiConfiguration.class);

    @Test
    void disabledByDefaultWithoutCredentials()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ApiClient.class).doesNotHaveBean(SellersApi.class);
        });
    }

    @Test
    void enabledWithoutCredentialsFailsBeforeNetworkAccess()
    {
        contextRunner.withPropertyValues("erp.amazon.sp-api.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Amazon SP-API 缺少配置：client-id");
        });
    }

    @Test
    void bindsCredentialsAndCreatesSingletonClientsWithBoundedTransport()
    {
        contextRunner.withPropertyValues("erp.amazon.sp-api.enabled=true",
                "erp.amazon.sp-api.client-id=synthetic-client-id",
                "erp.amazon.sp-api.client-secret=synthetic-client-secret",
                "erp.amazon.sp-api.refresh-token=synthetic-refresh-token",
                "erp.amazon.sp-api.connect-timeout=2s",
                "erp.amazon.sp-api.read-timeout=4s",
                "erp.amazon.sp-api.call-timeout=6s").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ApiClient.class).hasSingleBean(SellersApi.class);
            ApiClient client = context.getBean(ApiClient.class);
            assertSame(client, context.getBean(ApiClient.class));
            assertSame(context.getBean(SellersApi.class), context.getBean(SellersApi.class));
            assertEquals(AmazonSpApiProperties.API_ENDPOINT, client.getBasePath());
            assertEquals(2000, client.getConnectTimeout());
            assertEquals(4000, client.getReadTimeout());
            assertEquals(4000, client.getWriteTimeout());
            assertEquals(6000, client.getHttpClient().callTimeoutMillis());
            assertFalse(client.getHttpClient().followRedirects());
            assertFalse(client.getHttpClient().followSslRedirects());
            assertFalse(client.getHttpClient().retryOnConnectionFailure());
            String rendered = context.getBean(AmazonSpApiProperties.class).toString();
            assertFalse(rendered.contains("synthetic-client-id"));
            assertFalse(rendered.contains("synthetic-client-secret"));
            assertFalse(rendered.contains("synthetic-refresh-token"));
        });
    }

    @Test
    void rejectsUnboundedOrInvalidTimeouts()
    {
        for (Duration timeout : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(3), null})
        {
            AmazonSpApiProperties properties = AmazonLwaTokenProviderTest.testProperties();
            properties.setCallTimeout(timeout);
            assertThrows(IllegalStateException.class, properties::validateEnabledConfiguration);
        }
    }
}
