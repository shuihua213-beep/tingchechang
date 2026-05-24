package com.cf.sms.task;

import com.cf.sms.service.impl.SmsServiceImpl;
import com.cf.sms.service.utils.RedisUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SmsRetryTask {

    private static final long RECORD_LOCK_MINUTES = 5L;
    private static final long PHONE_COMPENSATE_LIMIT_MINUTES = 10L;

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SmsServiceImpl smsService;

    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(cron = "0 0/1 * * * *")
    public void retryFailedSms() {
        Set<String> failureRecordKeys = redisUtils.members(SmsServiceImpl.SMS_FAILURE_INDEX_KEY);
        if (failureRecordKeys == null || failureRecordKeys.isEmpty()) {
            return;
        }
        for (String failureRecordKey : failureRecordKeys) {
            if (StringUtils.isBlank(failureRecordKey)) {
                continue;
            }
            Map<String, String> failureRecord = redisUtils.entries(failureRecordKey);
            if (failureRecord == null || failureRecord.isEmpty()) {
                redisUtils.removeFromSet(SmsServiceImpl.SMS_FAILURE_INDEX_KEY, failureRecordKey);
                continue;
            }
            String smsId = failureRecord.get("smsId");
            String phone = failureRecord.get("phone");
            if (StringUtils.isAnyBlank(smsId, phone)) {
                smsService.clearFailureRecord(failureRecordKey);
                continue;
            }
            String recordLockKey = SmsServiceImpl.SMS_FAILURE_LOCK_PREFIX + smsId;
            Boolean lockResult = redisUtils.tryLock(recordLockKey, instanceId, RECORD_LOCK_MINUTES, TimeUnit.MINUTES);
            if (lockResult == null || !lockResult) {
                continue;
            }
            try {
                String phoneLimitKey = SmsServiceImpl.SMS_PHONE_COMPENSATE_LIMIT_PREFIX + phone;
                Boolean phoneLockResult = redisUtils.tryLock(phoneLimitKey, smsId, PHONE_COMPENSATE_LIMIT_MINUTES, TimeUnit.MINUTES);
                if (phoneLockResult == null || !phoneLockResult) {
                    continue;
                }
                smsService.retryFailedSms(failureRecordKey, failureRecord);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                redisUtils.delete(recordLockKey);
            }
        }
    }
}
