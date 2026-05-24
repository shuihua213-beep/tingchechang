package com.cf.framework.redis.utils;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void saveFailedRecord(String phone, String templateCode, String returnCode, String exceptionSummary, int retryCount,
                                 String templateParam, String signName, String regionId, String accessKeyId, String secret, String smsUpExtendCode, String outId) {
        JSONObject record = new JSONObject();
        record.put("phone", phone);
        record.put("templateCode", templateCode);
        record.put("returnCode", returnCode);
        record.put("exceptionSummary", exceptionSummary);
        record.put("retryCount", retryCount);
        record.put("templateParam", templateParam);
        record.put("signName", signName);
        record.put("regionId", regionId);
        record.put("accessKeyId", accessKeyId);
        record.put("secret", secret);
        record.put("smsUpExtendCode", smsUpExtendCode);
        record.put("outId", outId);

        String key = "sms_failed:" + phone;
        stringRedisTemplate.opsForValue().set(key, record.toJSONString(), 7, TimeUnit.DAYS);
    }

    public void deleteFailedRecord(String phone) {
        stringRedisTemplate.delete("sms_failed:" + phone);
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
}
