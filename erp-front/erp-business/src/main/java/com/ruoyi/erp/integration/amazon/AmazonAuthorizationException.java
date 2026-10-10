package com.ruoyi.erp.integration.amazon;

/** 不携带响应正文、凭据或原始异常，供后续同步任务分类处理。 */
public class AmazonAuthorizationException extends RuntimeException
{
    public enum Reason
    {
        AUTHORIZATION, THROTTLED, REMOTE_SERVICE, INVALID_RESPONSE, NETWORK
    }

    private final Reason reason;
    private final int httpStatus;

    public AmazonAuthorizationException(Reason reason, int httpStatus)
    {
        super("Amazon LWA 请求失败：" + reason + "（HTTP " + httpStatus + "）");
        this.reason = reason;
        this.httpStatus = httpStatus;
    }

    public Reason getReason() { return reason; }
    public int getHttpStatus() { return httpStatus; }
}
