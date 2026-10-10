package com.ruoyi.erp.integration.amazon;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ruoyi.erp.integration.amazon.AmazonAuthorizationException.Reason;
import static org.junit.jupiter.api.Assertions.*;

class AmazonLwaTokenProviderTest
{
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseJson = new AtomicReference<>(token("synthetic-access-token", 3600));
    private final MutableClock clock = new MutableClock();
    private final AmazonSpApiProperties properties = testProperties();
    private final OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
        assertEquals(AmazonSpApiProperties.LWA_ENDPOINT, chain.request().url().toString());
        assertEquals("POST", chain.request().method());
        assertInstanceOf(FormBody.class, chain.request().body());
        FormBody body = (FormBody) chain.request().body();
        assertEquals("refresh_token", body.value(0));
        assertEquals(properties.getClientId(), body.value(1));
        assertEquals(properties.getClientSecret(), body.value(2));
        assertEquals(properties.getRefreshToken(), body.value(3));
        requests.incrementAndGet();
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(responseStatus.get()).message("synthetic response")
                .body(ResponseBody.create(responseJson.get(), MediaType.get("application/json"))).build();
    }).build();
    private final AmazonLwaTokenProvider provider = new AmazonLwaTokenProvider(httpClient, properties, clock);

    @Test
    void reusesTokenThenRefreshesAtExpiryMargin()
    {
        assertEquals("synthetic-access-token", provider.getAccessToken());
        clock.advance(3539);
        assertEquals("synthetic-access-token", provider.getAccessToken());
        assertEquals(1, requests.get());
        clock.advance(1);
        responseJson.set(token("synthetic-next-token", 3600));
        assertEquals("synthetic-next-token", provider.getAccessToken());
        assertEquals(2, requests.get());
    }

    @Test
    void mergesConcurrentFirstRefresh() throws Exception
    {
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++)
            {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return provider.getAccessToken();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<String> result : results)
            {
                assertEquals("synthetic-access-token", result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, requests.get());
        }
        finally
        {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void authorizationFailureIsSafeAndCanRecoverWithoutCachingFailure()
    {
        responseStatus.set(400);
        responseJson.set("{\"error_description\":\"synthetic-client-secret synthetic-refresh-token\"}");
        AmazonAuthorizationException failure = assertThrows(AmazonAuthorizationException.class, provider::getAccessToken);
        assertEquals(Reason.AUTHORIZATION, failure.getReason());
        assertEquals(400, failure.getHttpStatus());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(properties.getClientSecret()));
        assertFalse(failure.toString().contains(properties.getRefreshToken()));
        responseStatus.set(200);
        responseJson.set(token("synthetic-recovered-token", 3600));
        assertEquals("synthetic-recovered-token", provider.getAccessToken());
        assertEquals(2, requests.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503, 302})
    void classifiesHttpFailuresWithoutImplicitRetry(int status)
    {
        responseStatus.set(status);
        AmazonAuthorizationException failure = assertThrows(AmazonAuthorizationException.class, provider::getAccessToken);
        assertEquals(status == 429 ? Reason.THROTTLED : status == 503 ? Reason.REMOTE_SERVICE : Reason.AUTHORIZATION,
                failure.getReason());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json", "{\"access_token\":123,\"expires_in\":3600}",
            "{\"access_token\":\"\",\"expires_in\":3600}",
            "{\"access_token\":\"token\",\"expires_in\":0}",
            "{\"access_token\":\"token\",\"expires_in\":1.5}",
            "{\"access_token\":\"token\\n\",\"expires_in\":3600}"})
    void rejectsInvalidTokenResponses(String json)
    {
        responseJson.set(json);
        AmazonAuthorizationException failure = assertThrows(AmazonAuthorizationException.class, provider::getAccessToken);
        assertEquals(Reason.INVALID_RESPONSE, failure.getReason());
        assertNull(failure.getCause());
    }

    @Test
    void rejectsOversizeResponse()
    {
        responseJson.set(" ".repeat(17000));
        assertEquals(Reason.INVALID_RESPONSE,
                assertThrows(AmazonAuthorizationException.class, provider::getAccessToken).getReason());
    }

    @Test
    void doesNotReturnExpiredTokenWhenRefreshFails()
    {
        provider.getAccessToken();
        clock.advance(3600);
        responseStatus.set(503);
        assertEquals(Reason.REMOTE_SERVICE,
                assertThrows(AmazonAuthorizationException.class, provider::getAccessToken).getReason());
        assertEquals(2, requests.get());
    }

    @Test
    void mapsNetworkFailureWithoutLeakingOriginalError()
    {
        OkHttpClient failingClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            throw new IOException("synthetic-client-secret");
        }).build();
        AmazonLwaTokenProvider failingProvider = new AmazonLwaTokenProvider(failingClient, properties, clock);
        AmazonAuthorizationException failure = assertThrows(AmazonAuthorizationException.class, failingProvider::getAccessToken);
        assertEquals(Reason.NETWORK, failure.getReason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(properties.getClientSecret()));
    }

    @Test
    void signsOnlyApprovedOriginAndReplacesExistingTokenHeader()
    {
        AmazonLwaAuthorizationSigner signer = new AmazonLwaAuthorizationSigner(properties, provider);
        Request request = new Request.Builder().url(AmazonSpApiProperties.API_ENDPOINT + "/sellers/v1/marketplaceParticipations")
                .header("x-amz-access-token", "old-synthetic-token").build();
        Request signed = signer.sign(request);
        assertEquals(List.of("synthetic-access-token"), signed.headers("x-amz-access-token"));
        assertEquals("old-synthetic-token", request.header("x-amz-access-token"));
        for (String url : List.of("https://example.invalid/", "http://sellingpartnerapi-na.amazon.com/",
                "https://sellingpartnerapi-na.amazon.com:444/"))
        {
            assertThrows(IllegalArgumentException.class, () -> signer.sign(new Request.Builder().url(url).build()));
        }
        assertEquals(1, requests.get());
    }

    static AmazonSpApiProperties testProperties()
    {
        AmazonSpApiProperties properties = new AmazonSpApiProperties();
        properties.setClientId("synthetic-client-id");
        properties.setClientSecret("synthetic-client-secret");
        properties.setRefreshToken("synthetic-refresh-token");
        return properties;
    }

    private static String token(String value, long expiresIn)
    {
        return "{\"access_token\":\"" + value + "\",\"expires_in\":" + expiresIn + "}";
    }

    private static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse("2026-10-10T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
