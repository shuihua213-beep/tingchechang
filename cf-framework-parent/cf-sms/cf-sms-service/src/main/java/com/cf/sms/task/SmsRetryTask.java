package com.cf.sms.task;

import com.alibaba.fastjson.JSONObject;
import com.cf.framework.redis.utils.RedisUtils;
import com.cf.sms.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SmsRetryTask {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SmsService smsService;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void retryFailedSms() {
        Set<String> keys = stringRedisTemplate.keys("sms_failed:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String phone = key.substring("sms_failed:".length());
            String lockKey = "lock:sms_retry:" + phone;

            Boolean locked = redisUtils.setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    String recordStr = stringRedisTemplate.opsForValue().get(key);
                    if (recordStr != null) {
                        JSONObject record = JSONObject.parseObject(recordStr);
                        
                        String templateCode = record.getString("templateCode");
                        String templateParam = record.getString("templateParam");
                        String signName = record.getString("signName");
                        String regionId = record.getString("regionId");
                        String accessKeyId = record.getString("accessKeyId");
                        String secret = record.getString("secret");
                        String smsUpExtendCode = record.getString("smsUpExtendCode");
                        String outId = record.getString("outId");

                        try {
                            smsService.sendSmsByAli(phone, templateParam, signName, templateCode, regionId, accessKeyId, secret, smsUpExtendCode, outId);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
