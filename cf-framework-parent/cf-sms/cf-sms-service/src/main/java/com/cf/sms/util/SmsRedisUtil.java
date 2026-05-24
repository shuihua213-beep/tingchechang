package com.cf.sms.util;

import com.alibaba.fastjson.JSON;
import com.cf.sms.domain.SmsFailRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SmsRedisUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String SMS_FAIL_RECORD_PREFIX = "sms:fail:";
    private static final String SMS_PHONE_COMPENSATE_PREFIX = "sms:compensate:";
    private static final long SMS_FAIL_RECORD_TTL = 86400;
    private static final long SMS_PHONE_COMPENSATE_TTL = 600;

    public void saveFailRecord(SmsFailRecord failRecord) {
        String key = SMS_FAIL_RECORD_PREFIX + failRecord.getId();
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(failRecord), SMS_FAIL_RECORD_TTL, TimeUnit.SECONDS);
    }

    public SmsFailRecord getFailRecord(String id) {
        String key = SMS_FAIL_RECORD_PREFIX + id;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            return JSON.parseObject(value, SmsFailRecord.class);
        }
        return null;
    }

    public void deleteFailRecord(String id) {
        String key = SMS_FAIL_RECORD_PREFIX + id;
        stringRedisTemplate.delete(key);
    }

    public Set<String> getAllFailRecordKeys() {
        return stringRedisTemplate.keys(SMS_FAIL_RECORD_PREFIX + "*");
    }

    public boolean tryLockPhoneCompensate(String phone) {
        String key = SMS_PHONE_COMPENSATE_PREFIX + phone;
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, System.currentTimeMillis() + "", SMS_PHONE_COMPENSATE_TTL, TimeUnit.SECONDS);
        return result != null && result;
    }

    public void unlockPhoneCompensate(String phone) {
        String key = SMS_PHONE_COMPENSATE_PREFIX + phone;
        stringRedisTemplate.delete(key);
    }
}
