package com.ruoyi.erp.integration.amazon;

import com.amazon.SellingPartnerAPIAA.LWAAuthorizationCredentials;
import com.amazon.SellingPartnerAPIAA.LWAAuthorizationSigner;
import okhttp3.Request;

/** 使用官方 SDK 的公开 signer 扩展点，不修改或反射访问 SDK 内部实现。 */
final class AmazonLwaAuthorizationSigner extends LWAAuthorizationSigner
{
    private final AmazonLwaTokenProvider tokenProvider;

    AmazonLwaAuthorizationSigner(AmazonSpApiProperties properties, AmazonLwaTokenProvider tokenProvider)
    {
        super(LWAAuthorizationCredentials.builder()
                .clientId(properties.getClientId()).clientSecret(properties.getClientSecret())
                .refreshToken(properties.getRefreshToken()).endpoint(AmazonSpApiProperties.LWA_ENDPOINT).build());
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Request sign(Request request)
    {
        if (!"https".equals(request.url().scheme())
                || !"sellingpartnerapi-na.amazon.com".equals(request.url().host())
                || request.url().port() != 443)
        {
            throw new IllegalArgumentException("SP-API 请求地址不属于已配置的美国站区域");
        }
        return request.newBuilder().header(SIGNED_ACCESS_TOKEN_HEADER_NAME, tokenProvider.getAccessToken()).build();
    }
}
