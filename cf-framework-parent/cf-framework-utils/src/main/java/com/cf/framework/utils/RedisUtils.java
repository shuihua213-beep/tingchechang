package com.cf.framework.utils;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String SMS_RETRY_RECORD_PREFIX = "sms:retry:record:";
    private static final String SMS_COMPENSATE_LOCK_PREFIX = "sms:compensate:lock:";
    private static final String SMS_RETRY_SET_PREFIX = "sms:retry:set";
    private static final long SMS_RETRY_RECORD_TTL_SECONDS = 7200L;
    private static final long COMPENSATE_LOCK_TTL_SECONDS = 600L;

    public void saveSmsRetryRecord(String smsId, String phone, String templateCode, String errorCode, String errorStack, int retryCount) {
        String key = SMS_RETRY_RECORD_PREFIX + smsId;
        SmsRetryRecord record = new SmsRetryRecord(phone, smsId, templateCode, errorCode, errorStack, retryCount, System.currentTimeMillis());
        String json = JSON.toJSONString(record);
        stringRedisTemplate.opsForValue().set(key, json, SMS_RETRY_RECORD_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForSet().add(SMS_RETRY_SET_PREFIX, smsId);
    }

    public SmsRetryRecord getSmsRetryRecord(String smsId) {
        String key = SMS_RETRY_RECORD_PREFIX + smsId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, SmsRetryRecord.class);
    }

    public void deleteSmsRetryRecord(String smsId) {
        String key = SMS_RETRY_RECORD_PREFIX + smsId;
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().remove(SMS_RETRY_SET_PREFIX, smsId);
    }

    public boolean acquireCompensateLock(String phone) {
        String key = SMS_COMPENSATE_LOCK_PREFIX + phone;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", COMPENSATE_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return success != null && success;
    }

    public void releaseCompensateLock(String phone) {
        String key = SMS_COMPENSATE_LOCK_PREFIX + phone;
        stringRedisTemplate.delete(key);
    }

    public void incrementRetryCount(String smsId) {
        String key = SMS_RETRY_RECORD_PREFIX + smsId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            SmsRetryRecord record = JSON.parseObject(json, SmsRetryRecord.class);
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastRetryTime(System.currentTimeMillis());
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(record), SMS_RETRY_RECORD_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    public Set<String> getAllRetryRecordIds() {
        return stringRedisTemplate.opsForSet().members(SMS_RETRY_SET_PREFIX);
    }

    public static class SmsRetryRecord {
        private String phone;
        private String smsId;
        private String templateCode;
        private String errorCode;
        private String errorStack;
        private int retryCount;
        private long lastRetryTime;

        public SmsRetryRecord() {
        }

        public SmsRetryRecord(String phone, String smsId, String templateCode, String errorCode, String errorStack, int retryCount, long lastRetryTime) {
            this.phone = phone;
            this.smsId = smsId;
            this.templateCode = templateCode;
            this.errorCode = errorCode;
            this.errorStack = errorStack;
            this.retryCount = retryCount;
            this.lastRetryTime = lastRetryTime;
        }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getSmsId() { return smsId; }
        public void setSmsId(String smsId) { this.smsId = smsId; }

        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        public String getErrorStack() { return errorStack; }
        public void setErrorStack(String errorStack) { this.errorStack = errorStack; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

        public long getLastRetryTime() { return lastRetryTime; }
        public void setLastRetryTime(long lastRetryTime) { this.lastRetryTime = lastRetryTime; }
    }
}
