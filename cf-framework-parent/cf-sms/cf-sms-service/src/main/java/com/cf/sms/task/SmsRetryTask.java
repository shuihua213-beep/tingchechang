package com.cf.sms.task;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.cf.framework.utils.RedisUtils;
import com.cf.sms.dao.mapper.CfSmsMapper;
import com.cf.sms.domain.CfSms;
import com.cf.ucenter.domain.CfWeixinConfig;
import com.cf.ucenter.service.CfWeixinConfigService;
import com.cf.ucenter.wxtools.WeiXinConfigUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SmsRetryTask {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryTask.class);
    private static final long COMPENSATE_RETRY_BACKOFF_MS = 2000L;

    @Autowired
    private CfSmsMapper cfSmsMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private CfWeixinConfigService cfWeixinConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void compensateFailedSms() {
        logger.info("SmsRetryTask: starting compensation scan");

        Set<String> retryRecordIds = redisUtils.getAllRetryRecordIds();
        if (retryRecordIds == null || retryRecordIds.isEmpty()) {
            logger.info("SmsRetryTask: no retry records found");
            return;
        }

        for (String smsId : retryRecordIds) {
            try {
                processRetryRecord(smsId);
            } catch (Exception e) {
                logger.error("SmsRetryTask: error processing smsId={}", smsId, e);
            }
        }
    }

    private void processRetryRecord(String smsId) {
        RedisUtils.SmsRetryRecord record = redisUtils.getSmsRetryRecord(smsId);
        if (record == null) {
            redisUtils.deleteSmsRetryRecord(smsId);
            return;
        }

        if (!redisUtils.acquireCompensateLock(record.getPhone())) {
            logger.info("SmsRetryTask: phone {} is being compensated by another node, skipping", record.getPhone());
            return;
        }

        try {
            CfSms cfSms = cfSmsMapper.selectByPrimaryKey(smsId);
            if (cfSms == null) {
                logger.warn("SmsRetryTask: sms record not found for smsId={}, cleaning up", smsId);
                redisUtils.deleteSmsRetryRecord(smsId);
                return;
            }

            if (cfSms.getStatus() == CfSms.STATUS_SUCCESS) {
                logger.info("SmsRetryTask: smsId={} already success, cleaning up", smsId);
                redisUtils.deleteSmsRetryRecord(smsId);
                return;
            }

            if (cfSms.getRetryCount() != null && cfSms.getRetryCount() >= CfSms.MAX_RETRY_COUNT) {
                logger.warn("SmsRetryTask: smsId={} exceeded max retry count ({}), marking as final failed", smsId, CfSms.MAX_RETRY_COUNT);
                redisUtils.deleteSmsRetryRecord(smsId);
                return;
            }

            logger.info("SmsRetryTask: retrying smsId={}, phone={}, currentRetryCount={}", smsId, record.getPhone(), cfSms.getRetryCount());

            Thread.sleep(COMPENSATE_RETRY_BACKOFF_MS);

            boolean success = retrySendSms(cfSms, record);

            if (success) {
                cfSmsMapper.updateSmsToSuccess(smsId, CfSms.STATUS_SUCCESS);
                redisUtils.deleteSmsRetryRecord(smsId);
                logger.info("SmsRetryTask: smsId={} compensation success", smsId);
            } else {
                int newRetryCount = (cfSms.getRetryCount() == null ? 0 : cfSms.getRetryCount()) + 1;
                cfSmsMapper.updateSmsStatus(smsId, CfSms.STATUS_FAILED, record.getErrorCode(), record.getErrorStack(), newRetryCount, System.currentTimeMillis());
                redisUtils.incrementRetryCount(smsId);
                logger.warn("SmsRetryTask: smsId={} compensation failed, new retryCount={}", smsId, newRetryCount);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("SmsRetryTask: interrupted while processing smsId={}", smsId, e);
        } finally {
            redisUtils.releaseCompensateLock(record.getPhone());
        }
    }

    private boolean retrySendSms(CfSms cfSms, RedisUtils.SmsRetryRecord record) {
        try {
            List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("ali_sms");
            if (cfWeixinConfigs == null || cfWeixinConfigs.isEmpty()) {
                logger.error("SmsRetryTask: no WeixinConfig found for retry");
                return false;
            }

            String signName = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("sign_name", cfWeixinConfigs);
            String templateCode = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("template_code", cfWeixinConfigs);
            String regionId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("region_id", cfWeixinConfigs);
            String accessKeyId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("access_key_id", cfWeixinConfigs);
            String secret = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret", cfWeixinConfigs);

            DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, secret);
            IAcsClient client = new DefaultAcsClient(profile);

            CommonRequest request = new CommonRequest();
            request.setMethod(MethodType.POST);
            request.setDomain("dysmsapi.aliyuncs.com");
            request.setVersion("2017-05-25");
            request.setAction("SendSms");
            request.putQueryParameter("RegionId", regionId);
            request.putQueryParameter("PhoneNumbers", cfSms.getPhone());
            request.putQueryParameter("SignName", signName);
            request.putQueryParameter("TemplateCode", templateCode);
            request.putQueryParameter("TemplateParam", "{\"code\":\"" + cfSms.getCode() + "\"}");

            CommonResponse response = client.getCommonResponse(request);
            String responseData = response.getData();
            JsonNode jsonNode = objectMapper.readTree(responseData);
            String code = jsonNode.has("Code") ? jsonNode.get("Code").asText() : null;
            return "OK".equals(code);
        } catch (Exception e) {
            logger.error("SmsRetryTask: retry send failed for smsId={}", cfSms.getId(), e);
            return false;
        }
    }
}
