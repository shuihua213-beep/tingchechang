package com.cf.framework.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value, long seconds) {
        stringRedisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    public Boolean setnx(String key, String value, long seconds) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value);
        if (result != null && result) {
            stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        }
        return result;
    }

    public void expire(String key, long seconds) {
        stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    public void hset(String key, String hashKey, String value) {
        stringRedisTemplate.opsForHash().put(key, hashKey, value);
    }

    public String hget(String key, String hashKey) {
        Object val = stringRedisTemplate.opsForHash().get(key, hashKey);
        return val != null ? val.toString() : null;
    }

    public Map<Object, Object> hgetAll(String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    public void hmset(String key, Map<String, String> map, long ttlSeconds) {
        stringRedisTemplate.opsForHash().putAll(key, map);
        if (ttlSeconds > 0) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public Long hdel(String key, String... hashKeys) {
        return stringRedisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    public Boolean hhasKey(String key, String hashKey) {
        return stringRedisTemplate.opsForHash().hasKey(key, hashKey);
    }

    public List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Cursor<String> cursor = stringRedisTemplate.scan(options);
        while (cursor.hasNext()) {
            keys.add(cursor.next());
        }
        try {
            cursor.close();
        } catch (Exception ignore) {
        }
        return keys;
    }

    public void saveSmsFailedRecord(String phone, String templateCode, String carrierReturnCode,
                                     String exceptionSummary, int retryCount, long ttlSeconds) {
        String key = "sms:failed:" + phone;
        stringRedisTemplate.opsForHash().put(key, "phone", phone);
        stringRedisTemplate.opsForHash().put(key, "templateCode", templateCode != null ? templateCode : "");
        stringRedisTemplate.opsForHash().put(key, "carrierReturnCode", carrierReturnCode != null ? carrierReturnCode : "");
        stringRedisTemplate.opsForHash().put(key, "exceptionSummary", exceptionSummary != null ? exceptionSummary : "");
        stringRedisTemplate.opsForHash().put(key, "retryCount", String.valueOf(retryCount));
        stringRedisTemplate.opsForHash().put(key, "createTime", String.valueOf(System.currentTimeMillis()));
        if (ttlSeconds > 0) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public void saveSmsFailedRecord(String phone, Integer type, String templateCode, String carrierReturnCode,
                                     String exceptionSummary, int retryCount, long ttlSeconds) {
        String key = "sms:failed:" + phone;
        stringRedisTemplate.opsForHash().put(key, "phone", phone);
        stringRedisTemplate.opsForHash().put(key, "type", type != null ? String.valueOf(type) : "");
        stringRedisTemplate.opsForHash().put(key, "templateCode", templateCode != null ? templateCode : "");
        stringRedisTemplate.opsForHash().put(key, "carrierReturnCode", carrierReturnCode != null ? carrierReturnCode : "");
        stringRedisTemplate.opsForHash().put(key, "exceptionSummary", exceptionSummary != null ? exceptionSummary : "");
        stringRedisTemplate.opsForHash().put(key, "retryCount", String.valueOf(retryCount));
        stringRedisTemplate.opsForHash().put(key, "createTime", String.valueOf(System.currentTimeMillis()));
        if (ttlSeconds > 0) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public Map<Object, Object> getSmsFailedRecord(String phone) {
        String key = "sms:failed:" + phone;
        return stringRedisTemplate.opsForHash().entries(key);
    }

    public void removeSmsFailedRecord(String phone) {
        String key = "sms:failed:" + phone;
        stringRedisTemplate.delete(key);
    }

    public List<Map<Object, Object>> getAllSmsFailedRecords() {
        List<String> keys = scanKeys("sms:failed:*");
        List<Map<Object, Object>> records = new ArrayList<>();
        for (String key : keys) {
            Map<Object, Object> record = stringRedisTemplate.opsForHash().entries(key);
            if (record != null && !record.isEmpty()) {
                records.add(record);
            }
        }
        return records;
    }
}