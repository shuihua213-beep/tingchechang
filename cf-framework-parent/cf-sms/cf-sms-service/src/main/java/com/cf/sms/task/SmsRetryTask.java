package com.cf.sms.task;

import com.cf.sms.domain.CfSms;
import com.cf.sms.domain.SmsFailRecord;
import com.cf.sms.service.impl.SmsServiceImpl;
import com.cf.sms.util.SmsRedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SmsRetryTask {

    private static final Logger log = LoggerFactory.getLogger(SmsRetryTask.class);
    private static final int MAX_COMPENSATE_RETRY_COUNT = 3;

    @Autowired
    private SmsRedisUtil smsRedisUtil;

    @Autowired
    private SmsServiceImpl smsService;

    @Scheduled(fixedDelay = 60000)
    public void retryFailedSms() {
        log.info("开始执行短信补偿任务...");
        Set<String> failRecordKeys = smsRedisUtil.getAllFailRecordKeys();
        
        if (failRecordKeys == null || failRecordKeys.isEmpty()) {
            log.info("没有待补偿的短信记录");
            return;
        }

        for (String key : failRecordKeys) {
            String id = key.substring(key.lastIndexOf(":") + 1);
            SmsFailRecord failRecord = smsRedisUtil.getFailRecord(id);
            
            if (failRecord == null) {
                continue;
            }

            processFailRecord(failRecord);
        }
        
        log.info("短信补偿任务执行完成");
    }

    private void processFailRecord(SmsFailRecord failRecord) {
        String phone = failRecord.getPhone();
        
        if (!smsRedisUtil.tryLockPhoneCompensate(phone)) {
            log.info("手机号 {} 正在被其他节点补偿，跳过", phone);
            return;
        }

        try {
            if (failRecord.getRetryCount() >= MAX_COMPENSATE_RETRY_COUNT) {
                log.warn("短信记录 {} 已达到最大补偿次数 {}，将从 Redis 中删除", failRecord.getId(), MAX_COMPENSATE_RETRY_COUNT);
                smsRedisUtil.deleteFailRecord(failRecord.getId());
                return;
            }

            log.info("开始补偿短信，id: {}, phone: {}, 重试次数: {}", failRecord.getId(), phone, failRecord.getRetryCount());
            
            try {
                smsService.sendSmsByAliOnce(
                    failRecord.getPhone(),
                    failRecord.getTemplateParam(),
                    failRecord.getSignName(),
                    failRecord.getTemplateCode(),
                    failRecord.getRegionId(),
                    failRecord.getAccessKeyId(),
                    failRecord.getSecret(),
                    failRecord.getSmsUpExtendCode(),
                    failRecord.getOutId()
                );
                
                log.info("短信补偿成功，id: {}, phone: {}", failRecord.getId(), phone);
                smsRedisUtil.deleteFailRecord(failRecord.getId());
                
                CfSms cfSms = new CfSms();
                cfSms.setId(failRecord.getId());
                cfSms.setStatus(CfSms.SMS_STATUS_SENT);
                
            } catch (Exception e) {
                log.error("短信补偿失败，id: {}, phone: {}", failRecord.getId(), phone, e);
                failRecord.setRetryCount(failRecord.getRetryCount() + 1);
                failRecord.setLastRetryTime(System.currentTimeMillis());
                failRecord.setErrorSummary(e.getMessage() != null && e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());
                smsRedisUtil.saveFailRecord(failRecord);
            }
        } finally {
            smsRedisUtil.unlockPhoneCompensate(phone);
        }
    }
}
