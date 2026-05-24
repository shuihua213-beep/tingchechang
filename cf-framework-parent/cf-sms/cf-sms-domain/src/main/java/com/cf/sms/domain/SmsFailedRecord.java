package com.cf.sms.domain;

import java.io.Serializable;

public class SmsFailedRecord implements Serializable {

    private String phone;
    private String templateCode;
    private String carrierReturnCode;
    private String exceptionSummary;
    private int retryCount;
    private long createTime;

    public static final String REDIS_KEY_PREFIX = "sms:failed:";
    public static final String RETRY_LOCK_PREFIX = "sms:retry:lock:";
    public static final long RETRY_LOCK_TTL_SECONDS = 600L;
    public static final long FAILED_RECORD_TTL_SECONDS = 86400L;
    public static final int MAX_RETRY_COUNT = 3;
    public static final long INITIAL_BACKOFF_MS = 1000L;

    public SmsFailedRecord() {
    }

    public SmsFailedRecord(String phone, String templateCode, String carrierReturnCode,
                           String exceptionSummary, int retryCount, long createTime) {
        this.phone = phone;
        this.templateCode = templateCode;
        this.carrierReturnCode = carrierReturnCode;
        this.exceptionSummary = exceptionSummary;
        this.retryCount = retryCount;
        this.createTime = createTime;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getCarrierReturnCode() {
        return carrierReturnCode;
    }

    public void setCarrierReturnCode(String carrierReturnCode) {
        this.carrierReturnCode = carrierReturnCode;
    }

    public String getExceptionSummary() {
        return exceptionSummary;
    }

    public void setExceptionSummary(String exceptionSummary) {
        this.exceptionSummary = exceptionSummary;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String buildRedisKey() {
        return REDIS_KEY_PREFIX + this.phone;
    }

    public long nextBackoffMillis() {
        return INITIAL_BACKOFF_MS * (1L << Math.min(retryCount, 5));
    }
}