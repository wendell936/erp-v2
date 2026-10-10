package com.ruoyi.erp.integration.amazon;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import software.amazon.spapi.ApiClient;
import software.amazon.spapi.ApiException;
import software.amazon.spapi.api.sellers.v1.SellersApi;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实官方生成代码和本项目依赖解析结果，HTTP 拦截器完全替代网络。 */
class AmazonSdkCompatibilityTest
{
    private final AtomicInteger lwaRequests = new AtomicInteger();
    private final AtomicInteger apiRequests = new AtomicInteger();

    private SellersApi sellersApi(int apiStatus)
    {
        OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            String json;
            int status;
            if (AmazonSpApiProperties.LWA_ENDPOINT.equals(chain.request().url().toString()))
            {
                lwaRequests.incrementAndGet();
                json = "{\"access_token\":\"synthetic-token\",\"expires_in\":3600}";
                status = 200;
            }
            else
            {
                assertEquals(AmazonSpApiProperties.API_ENDPOINT + "/sellers/v1/marketplaceParticipations",
                        chain.request().url().toString());
                assertEquals("GET", chain.request().method());
                assertEquals("synthetic-token", chain.request().header("x-amz-access-token"));
                assertEquals("erp-v2/3.9.2 (Language=Java)", chain.request().header("User-Agent"));
                apiRequests.incrementAndGet();
                status = apiStatus;
                json = apiStatus == 200
                        ? "{\"payload\":[{\"marketplace\":{\"id\":\"ATVPDKIKX0DER\",\"countryCode\":\"US\","
                                + "\"defaultCurrencyCode\":\"USD\"},\"participation\":{\"isParticipating\":true}}]}"
                        : "{\"errors\":[{\"code\":\"Unauthorized\",\"message\":\"synthetic failure\"}]}";
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                    .message("synthetic response").header("x-amzn-RequestId", "synthetic-request-id")
                    .body(ResponseBody.create(json, MediaType.get("application/json"))).build();
        }).build();
        AmazonSpApiProperties properties = AmazonLwaTokenProviderTest.testProperties();
        ApiClient client = new ApiClient(httpClient).setBasePath(AmazonSpApiProperties.API_ENDPOINT)
                .setUserAgent("erp-v2/3.9.2 (Language=Java)")
                .setLWAAuthorizationSigner(new AmazonLwaAuthorizationSigner(properties,
                        new AmazonLwaTokenProvider(httpClient, properties, Clock.systemUTC())));
        return new SellersApi(client, false);
    }

    @Test
    void generatedReadRequestParsesModelAndPreservesRequestId() throws Exception
    {
        SellersApi api = sellersApi(200);
        var response = api.getMarketplaceParticipationsWithHttpInfo();
        assertEquals(200, response.getStatusCode());
        var participation = response.getData().getPayload().get(0);
        assertEquals("ATVPDKIKX0DER", participation.getMarketplace().getId());
        assertEquals("USD", participation.getMarketplace().getDefaultCurrencyCode());
        assertTrue(participation.getParticipation().getIsParticipating());
        assertTrue(response.getHeaders().entrySet().stream()
                .anyMatch(header -> header.getKey().equalsIgnoreCase("x-amzn-RequestId")
                        && header.getValue().contains("synthetic-request-id")));
        api.getMarketplaceParticipations();
        assertEquals(1, lwaRequests.get());
        assertEquals(2, apiRequests.get());
    }

    @Test
    void retainsSdkLocalRateLimit() throws Exception
    {
        SellersApi api = sellersApi(200);
        api.getMarketplaceParticipationsBucket.tryConsumeAsMuchAsPossible();
        assertThrows(ApiException.RateLimitExceeded.class, api::getMarketplaceParticipations);
        assertEquals(0, apiRequests.get());
    }

    @Test
    void preservesHttpFailureForFutureTaskRecoveryWithoutImplicitRetry()
    {
        SellersApi api = sellersApi(403);
        ApiException failure = assertThrows(ApiException.class, api::getMarketplaceParticipations);
        assertEquals(403, failure.getCode());
        assertNotNull(failure.getResponseHeaders());
        assertEquals(1, apiRequests.get());
    }

    @Test
    void targetApiVersionsArePackaged() throws Exception
    {
        for (String className : new String[] {
                "software.amazon.spapi.api.catalogitems.v2022_04_01.CatalogApi",
                "software.amazon.spapi.api.listings.items.v2021_08_01.ListingsApi",
                "software.amazon.spapi.api.fba.inventory.v1.FbaInventoryApi",
                "software.amazon.spapi.api.fulfillment.inbound.v2024_03_20.FbaInboundApi",
                "software.amazon.spapi.api.orders.v2026_01_01.SearchOrdersApi",
                "software.amazon.spapi.api.orders.v2026_01_01.GetOrderApi",
                "software.amazon.spapi.api.finances.v2024_06_19.DefaultApi",
                "software.amazon.spapi.api.reports.v2021_06_30.ReportsApi"})
        {
            assertNotNull(Class.forName(className));
        }
    }
}
