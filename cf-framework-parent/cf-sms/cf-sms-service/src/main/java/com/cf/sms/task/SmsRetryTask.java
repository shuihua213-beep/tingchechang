package com.cf.sms.task;

import com.cf.framework.utils.RedisUtils;
import com.cf.sms.dao.mapper.CfSmsMapper;
import com.cf.sms.domain.CfSms;
import com.cf.sms.domain.SmsFailedRecord;
import com.cf.sms.service.SmsService;
import com.cf.ucenter.domain.CfWeixinConfig;
import com.cf.ucenter.service.CfWeixinConfigService;
import com.cf.ucenter.wxtools.WeiXinConfigUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SmsRetryTask {

    private static final Logger log = LoggerFactory.getLogger(SmsRetryTask.class);

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private CfSmsMapper cfSmsMapper;

    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private SmsService smsService;

    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private CfWeixinConfigService cfWeixinConfigService;

    @Scheduled(fixedDelay = 60000)
    public void retryFailedSms() {
        log.info("SmsRetryTask start scanning failed SMS records");
        List<Map<Object, Object>> failedRecords = redisUtils.getAllSmsFailedRecords();
        if (failedRecords == null || failedRecords.isEmpty()) {
            return;
        }

        for (Map<Object, Object> record : failedRecords) {
            processFailedRecord(record);
        }
        log.info("SmsRetryTask completed, processed {} failed records", failedRecords.size());
    }

    private void processFailedRecord(Map<Object, Object> record) {
        String phone = getString(record, "phone");
        if (phone == null || phone.isEmpty()) {
            return;
        }

        String lockKey = SmsFailedRecord.RETRY_LOCK_PREFIX + phone;
        Boolean lockAcquired = redisUtils.setnx(lockKey, "1", SmsFailedRecord.RETRY_LOCK_TTL_SECONDS);
        if (lockAcquired == null || !lockAcquired) {
            log.debug("SmsRetryTask skip phone={}, already locked by another node", phone);
            return;
        }

        try {
            String templateCode = getString(record, "templateCode");
            int retryCount = getInt(record, "retryCount");
            Integer type = getInt(record, "type");
            long createTime = getLong(record, "createTime");

            long recordAgeMs = System.currentTimeMillis() - createTime;
            long halfLockTtlMs = SmsFailedRecord.RETRY_LOCK_TTL_SECONDS * 1000 / 2;
            if (recordAgeMs < halfLockTtlMs && retryCount >= SmsFailedRecord.MAX_RETRY_COUNT) {
                log.info("SmsRetryTask skip phone={}, retryCount={} already at max, record age={}ms", phone, retryCount, recordAgeMs);
                return;
            }

            List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("ali_sms");
            String signName = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("sign_name", cfWeixinConfigs);
            String tmplCode = StringUtilsNotEmpty(templateCode) ? templateCode
                    : WeiXinConfigUtils.getWeiXinConfigragtionByEnName("template_code", cfWeixinConfigs);
            String regionId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("region_id", cfWeixinConfigs);
            String accessKeyId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("access_key_id", cfWeixinConfigs);
            String secret = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret", cfWeixinConfigs);

            cfSmsMapper.updateStatusByPhoneAndType(phone, type, CfSms.SMS_STATUS_RETRYING);

            String code = (int)((Math.random()*9+1)*100000)+"";
            String templateParam = "{\"code\":\"" + code + "\"}";

            boolean success = smsService.sendSmsByAliWithRetry(phone, templateParam, signName, tmplCode,
                    regionId, accessKeyId, secret, "", "", type);

            if (success) {
                redisUtils.removeSmsFailedRecord(phone);
                cfSmsMapper.updateStatusByPhoneAndType(phone, type, CfSms.SMS_STATUS_SENT);
                log.info("SmsRetryTask retry success for phone={}, type={}", phone, type);
            }
        } catch (Exception e) {
            log.error("SmsRetryTask retry failed for phone={}", phone, e);
        }
    }

    private boolean StringUtilsNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    private String getString(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private int getInt(Map<Object, Object> map, String key) {
        try {
            Object val = map.get(key);
            return val != null ? Integer.parseInt(val.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long getLong(Map<Object, Object> map, String key) {
        try {
            Object val = map.get(key);
            return val != null ? Long.parseLong(val.toString()) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}