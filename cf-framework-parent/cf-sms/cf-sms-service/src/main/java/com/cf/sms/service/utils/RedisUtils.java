package com.cf.sms.service.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void putAll(String key, Map<String, String> values, long timeout, TimeUnit timeUnit) {
        if (StringUtils.isBlank(key) || values == null || values.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForHash().putAll(key, values);
        if (timeout > 0) {
            stringRedisTemplate.expire(key, timeout, timeUnit);
        }
    }

    public Map<String, String> entries(String key) {
        if (StringUtils.isBlank(key)) {
            return Collections.emptyMap();
        }
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    public Boolean tryLock(String key, String value, long timeout, TimeUnit timeUnit) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value == null ? "" : value, timeout, timeUnit);
    }

    public void addToSet(String key, String value, long timeout, TimeUnit timeUnit) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
            return;
        }
        stringRedisTemplate.opsForSet().add(key, value);
        if (timeout > 0) {
            stringRedisTemplate.expire(key, timeout, timeUnit);
        }
    }

    public Set<String> members(String key) {
        if (StringUtils.isBlank(key)) {
            return Collections.emptySet();
        }
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return members == null ? Collections.<String>emptySet() : members;
    }

    public void removeFromSet(String key, String value) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
            return;
        }
        stringRedisTemplate.opsForSet().remove(key, value);
    }

    public void delete(String key) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        stringRedisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        return hasKey != null && hasKey;
    }
}
