package com.cf.sms.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.cf.framework.domain.response.CommonCode;
import com.cf.framework.domain.sms.response.SmsCode;
import com.cf.framework.domain.ucenter.response.UcenterCode;
import com.cf.framework.exception.ExceptionCast;
import com.cf.framework.utils.IdWorker;
import com.cf.sms.dao.mapper.CfSmsMapper;
import com.cf.sms.domain.CfSms;
import com.cf.sms.service.SmsService;
import com.cf.sms.service.utils.RedisUtils;
import com.cf.ucenter.domain.CfWeixinConfig;
import com.cf.ucenter.service.CfWeixinConfigService;
import com.cf.ucenter.wxtools.WeiXinConfigUtils;
import com.tencentcloudapi.captcha.v20190722.CaptchaClient;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaMiniResultRequest;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaMiniResultResponse;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultRequest;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service(version = "1.0.0", loadbalance = "roundrobin")
public class SmsServiceImpl implements SmsService {

    public static final String SMS_FAILURE_INDEX_KEY = "sms:failure:index";
    public static final String SMS_FAILURE_LOCK_PREFIX = "sms:failure:lock:";
    public static final String SMS_PHONE_COMPENSATE_LIMIT_PREFIX = "sms:failure:phone:";

    private static final String SMS_FAILURE_RECORD_PREFIX = "sms:failure:record:";
    private static final int MAX_RETRY_TIMES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 500L;
    private static final long MAX_BACKOFF_MILLIS = 4000L;
    private static final long SMS_FAILURE_RECORD_TTL_DAYS = 3L;
    private static final int STACK_TRACE_SUMMARY_LENGTH = 1000;

    @Autowired
    private CfSmsMapper cfSmsMapper;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisUtils redisUtils;
    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private CfWeixinConfigService cfWeixinConfigService;

    @Override
    public void sendSms(String phone, Integer type) {
        checkSendFrequently(phone, type);
        String code = (int) ((Math.random() * 9 + 1) * 100000) + "";
        long currentTime = System.currentTimeMillis();
        CfSms cfSms = new CfSms(idWorker.nextId(), phone, code, type, CfSms.SMS_STATUS_PENDING, currentTime,
                currentTime + CfSms.SMS_CODE_VALID_TIME);
        cfSmsMapper.insert(cfSms);

        AliSmsConfig aliSmsConfig = loadAliSmsConfig();
        sendSmsInternal(cfSms, phone, buildTemplateParam(code), aliSmsConfig.getSignName(), aliSmsConfig.getTemplateCode(),
                aliSmsConfig.getRegionId(), aliSmsConfig.getAccessKeyId(), aliSmsConfig.getSecret(), "", "", 0);
    }

    @Override
    public void checkSendFrequently(String phone, Integer type) {
        List<CfSms> lastSendLog = cfSmsMapper.getLastSendLog(phone, type);
        if (lastSendLog != null && lastSendLog.size() > 0
                && lastSendLog.get(0).getCreateTime() + CfSms.SMS_SEND_FREQUENTLY_LIMIT_TIME > System.currentTimeMillis()) {
            ExceptionCast.cast(SmsCode.SMS_SEND_FREQUENTLY);
        }
    }

    @Override
    public void sendSmsByAli(String PhoneNumbers, String TemplateParam, String signName, String templateCode, String regionId,
                             String accessKeyId, String secret, String SmsUpExtendCode, String OutId) {
        sendSmsInternal(null, PhoneNumbers, TemplateParam, signName, templateCode, regionId, accessKeyId, secret,
                SmsUpExtendCode, OutId, 0);
    }

    public void retryFailedSms(String failureRecordKey, Map<String, String> failureRecord) {
        if (failureRecord == null || failureRecord.isEmpty()) {
            clearFailureRecord(failureRecordKey);
            return;
        }
        String smsId = failureRecord.get("smsId");
        if (StringUtils.isBlank(smsId)) {
            clearFailureRecord(failureRecordKey);
            return;
        }
        CfSms cfSms = cfSmsMapper.selectByPrimaryKey(smsId);
        if (cfSms == null) {
            clearFailureRecord(failureRecordKey);
            return;
        }
        if (!CfSms.SMS_STATUS_PENDING.equals(cfSms.getStatus())) {
            clearFailureRecord(failureRecordKey);
            return;
        }
        if (cfSms.getExpireTime() == null || cfSms.getExpireTime() <= System.currentTimeMillis()) {
            clearFailureRecord(failureRecordKey);
            return;
        }
        AliSmsConfig aliSmsConfig = loadAliSmsConfig();
        String phone = StringUtils.defaultIfBlank(failureRecord.get("phone"), cfSms.getPhone());
        String templateParam = StringUtils.defaultIfBlank(failureRecord.get("templateParam"), buildTemplateParam(cfSms.getCode()));
        String signName = StringUtils.defaultIfBlank(failureRecord.get("signName"), aliSmsConfig.getSignName());
        String templateCode = StringUtils.defaultIfBlank(failureRecord.get("templateCode"), aliSmsConfig.getTemplateCode());
        String smsUpExtendCode = StringUtils.defaultString(failureRecord.get("smsUpExtendCode"));
        String outId = StringUtils.defaultString(failureRecord.get("outId"));
        int baseRetryCount = parseRetryCount(failureRecord.get("retryCount"));
        sendSmsInternal(cfSms, phone, templateParam, signName, templateCode, aliSmsConfig.getRegionId(),
                aliSmsConfig.getAccessKeyId(), aliSmsConfig.getSecret(), smsUpExtendCode, outId, baseRetryCount);
    }

    public void clearFailureRecord(String failureRecordKey) {
        if (StringUtils.isBlank(failureRecordKey)) {
            return;
        }
        redisUtils.delete(failureRecordKey);
        redisUtils.removeFromSet(SMS_FAILURE_INDEX_KEY, failureRecordKey);
    }

    @Override
    public void checkCode(String phone, String code, Integer type) {
        String phoneSmsCheckCounts = null;
        String redisKey = phone + "_" + type;
        try {
            phoneSmsCheckCounts = stringRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            e.printStackTrace();
            ExceptionCast.cast(CommonCode.SERVER_ERROR, e.getMessage());
        }
        if (phoneSmsCheckCounts != null && Integer.parseInt(phoneSmsCheckCounts) >= 5) {
            ExceptionCast.cast(SmsCode.CHECKING_TOO_FREQUENTLY);
        }

        int i = cfSmsMapper.updateLastValidSmsCodeStatus(phone, code, type, System.currentTimeMillis());
        if (i == 0) {
            if (phoneSmsCheckCounts != null) {
                Long expire = stringRedisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
                Long time = expire != null && expire > 0 ? expire : 300000L;
                stringRedisTemplate.boundValueOps(redisKey).set((Integer.parseInt(phoneSmsCheckCounts) + 1) + "", time, TimeUnit.MILLISECONDS);
            } else {
                stringRedisTemplate.boundValueOps(redisKey).set("1", 300000L, TimeUnit.MILLISECONDS);
            }
            ExceptionCast.cast(SmsCode.SMS_CODE_INVALID);
        }
        stringRedisTemplate.delete(redisKey);
    }

    @Override
    public void checkTencentMpCaptcha(String ip, String ticket, String platform, String randstr) throws Exception {

        List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("tencent_captcha");
        String secretId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret_id", cfWeixinConfigs);
        String secretKey = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret_key", cfWeixinConfigs);
        String captchaAppId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName(platform + "_captcha_app_id", cfWeixinConfigs);
        String appSecretKey = WeiXinConfigUtils.getWeiXinConfigragtionByEnName(platform + "_app_secret_key", cfWeixinConfigs);

        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("captcha.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        CaptchaClient client = new CaptchaClient(cred, "", clientProfile);

        if (StringUtils.isNotEmpty(randstr)) {
            DescribeCaptchaResultRequest describeCaptchaResultRequest = new DescribeCaptchaResultRequest();
            describeCaptchaResultRequest.setCaptchaType(9L);
            describeCaptchaResultRequest.setTicket(ticket);
            describeCaptchaResultRequest.setUserIp(ip);
            describeCaptchaResultRequest.setCaptchaAppId(new Long(captchaAppId));
            describeCaptchaResultRequest.setAppSecretKey(appSecretKey);
            describeCaptchaResultRequest.setRandstr(randstr);
            DescribeCaptchaResultResponse describeCaptchaResultResponse = client.DescribeCaptchaResult(describeCaptchaResultRequest);
            if (describeCaptchaResultResponse.getCaptchaCode() != 1) {
                ExceptionCast.cast(UcenterCode.CAPTCHA_NOT_MATCH);
            }
        } else {
            DescribeCaptchaMiniResultRequest req = new DescribeCaptchaMiniResultRequest();
            req.setCaptchaType(9L);
            req.setTicket(ticket);
            req.setUserIp(ip);
            req.setCaptchaAppId(new Long(captchaAppId));
            req.setAppSecretKey(appSecretKey);
            DescribeCaptchaMiniResultResponse resp = client.DescribeCaptchaMiniResult(req);
            if (resp.getCaptchaCode() != 1) {
                ExceptionCast.cast(UcenterCode.CAPTCHA_NOT_MATCH);
            }
        }
    }

    private void sendSmsInternal(CfSms cfSms, String phoneNumbers, String templateParam, String signName, String templateCode,
                                 String regionId, String accessKeyId, String secret, String smsUpExtendCode, String outId,
                                 int baseRetryCount) {
        int currentRetryCount = 0;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        String providerCode = "";
        String providerMessage = "";
        String lastExceptionSummary = "";
        while (currentRetryCount < MAX_RETRY_TIMES) {
            currentRetryCount++;
            try {
                CommonResponse response = doSendSms(phoneNumbers, templateParam, signName, templateCode, regionId, accessKeyId,
                        secret, smsUpExtendCode, outId);
                SmsSendResult sendResult = parseSendResult(response);
                if (sendResult.isSuccess()) {
                    if (cfSms != null) {
                        markSmsSendSuccess(cfSms.getId());
                        clearFailureRecord(buildFailureRecordKey(cfSms.getId()));
                    }
                    return;
                }
                providerCode = sendResult.getProviderCode();
                providerMessage = sendResult.getProviderMessage();
                lastExceptionSummary = sendResult.getLastExceptionSummary();
            } catch (ServerException e) {
                providerCode = "ALIYUN_SERVER_EXCEPTION";
                providerMessage = e.getMessage();
                lastExceptionSummary = summarizeException(e);
            } catch (ClientException e) {
                providerCode = "ALIYUN_CLIENT_EXCEPTION";
                providerMessage = e.getMessage();
                lastExceptionSummary = summarizeException(e);
            } catch (Exception e) {
                providerCode = e.getClass().getSimpleName();
                providerMessage = e.getMessage();
                lastExceptionSummary = summarizeException(e);
            }
            if (currentRetryCount < MAX_RETRY_TIMES) {
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    providerCode = "INTERRUPTED";
                    providerMessage = e.getMessage();
                    lastExceptionSummary = summarizeException(e);
                    break;
                }
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
            }
        }
        int totalRetryCount = baseRetryCount + currentRetryCount;
        if (cfSms != null) {
            saveFailureRecord(cfSms, phoneNumbers, templateParam, signName, templateCode, smsUpExtendCode, outId,
                    providerCode, providerMessage, lastExceptionSummary, totalRetryCount);
        }
        ExceptionCast.cast(CommonCode.FAIL, buildFailMessage(providerCode, providerMessage, lastExceptionSummary));
    }

    private CommonResponse doSendSms(String phoneNumbers, String templateParam, String signName, String templateCode,
                                     String regionId, String accessKeyId, String secret, String smsUpExtendCode,
                                     String outId) throws Exception {
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, secret);
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", regionId);
        if (StringUtils.isNotEmpty(phoneNumbers)) {
            request.putQueryParameter("PhoneNumbers", phoneNumbers);
        }
        if (StringUtils.isNotEmpty(signName)) {
            request.putQueryParameter("SignName", signName);
        }
        if (StringUtils.isNotEmpty(templateCode)) {
            request.putQueryParameter("TemplateCode", templateCode);
        }
        if (StringUtils.isNotEmpty(templateParam)) {
            request.putQueryParameter("TemplateParam", templateParam);
        }
        if (StringUtils.isNotEmpty(smsUpExtendCode)) {
            request.putQueryParameter("SmsUpExtendCode", smsUpExtendCode);
        }
        if (StringUtils.isNotEmpty(outId)) {
            request.putQueryParameter("OutId", outId);
        }
        return client.getCommonResponse(request);
    }

    private SmsSendResult parseSendResult(CommonResponse response) {
        if (response == null) {
            return new SmsSendResult(false, "EMPTY_RESPONSE", "短信网关无响应", "短信网关无响应");
        }
        String responseData = response.getData();
        if (StringUtils.isBlank(responseData)) {
            return new SmsSendResult(false, "EMPTY_RESPONSE", "短信网关返回空数据", "短信网关返回空数据");
        }
        JSONObject responseObject = JSON.parseObject(responseData);
        String code = responseObject.getString("Code");
        String message = responseObject.getString("Message");
        if (StringUtils.equalsIgnoreCase("OK", code)) {
            return new SmsSendResult(true, code, message, "");
        }
        String summary = StringUtils.abbreviate(code + ":" + StringUtils.defaultString(message), STACK_TRACE_SUMMARY_LENGTH);
        return new SmsSendResult(false, StringUtils.defaultIfBlank(code, "ALIYUN_RESPONSE_FAIL"),
                StringUtils.defaultIfBlank(message, responseData), summary);
    }

    private void markSmsSendSuccess(String smsId) {
        if (StringUtils.isBlank(smsId)) {
            return;
        }
        cfSmsMapper.updateSmsStatus(smsId, CfSms.SMS_STATUS_PENDING, CfSms.SMS_STATUS_UNUSED);
    }

    private void saveFailureRecord(CfSms cfSms, String phoneNumbers, String templateParam, String signName, String templateCode,
                                   String smsUpExtendCode, String outId, String providerCode, String providerMessage,
                                   String lastExceptionSummary, int retryCount) {
        String failureRecordKey = buildFailureRecordKey(cfSms.getId());
        Map<String, String> failureRecord = new HashMap<String, String>();
        failureRecord.put("smsId", cfSms.getId());
        failureRecord.put("phone", StringUtils.defaultIfBlank(phoneNumbers, cfSms.getPhone()));
        failureRecord.put("type", cfSms.getType() == null ? "" : String.valueOf(cfSms.getType()));
        failureRecord.put("templateCode", StringUtils.defaultString(templateCode));
        failureRecord.put("templateParam", StringUtils.defaultString(templateParam));
        failureRecord.put("signName", StringUtils.defaultString(signName));
        failureRecord.put("smsUpExtendCode", StringUtils.defaultString(smsUpExtendCode));
        failureRecord.put("outId", StringUtils.defaultString(outId));
        failureRecord.put("providerCode", StringUtils.defaultIfBlank(providerCode, "UNKNOWN"));
        failureRecord.put("providerMessage", StringUtils.defaultString(providerMessage));
        failureRecord.put("lastExceptionSummary", StringUtils.defaultIfBlank(lastExceptionSummary, "短信发送失败"));
        failureRecord.put("retryCount", String.valueOf(retryCount));
        failureRecord.put("createTime", cfSms.getCreateTime() == null ? "" : String.valueOf(cfSms.getCreateTime()));
        failureRecord.put("expireTime", cfSms.getExpireTime() == null ? "" : String.valueOf(cfSms.getExpireTime()));
        failureRecord.put("lastRetryTime", String.valueOf(System.currentTimeMillis()));
        redisUtils.putAll(failureRecordKey, failureRecord, SMS_FAILURE_RECORD_TTL_DAYS, TimeUnit.DAYS);
        redisUtils.addToSet(SMS_FAILURE_INDEX_KEY, failureRecordKey, SMS_FAILURE_RECORD_TTL_DAYS, TimeUnit.DAYS);
    }

    private String buildFailureRecordKey(String smsId) {
        return SMS_FAILURE_RECORD_PREFIX + smsId;
    }

    private int parseRetryCount(String retryCount) {
        if (StringUtils.isBlank(retryCount)) {
            return 0;
        }
        try {
            return Integer.parseInt(retryCount);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String summarizeException(Exception e) {
        return StringUtils.abbreviate(ExceptionUtils.getStackTrace(e), STACK_TRACE_SUMMARY_LENGTH);
    }

    private String buildFailMessage(String providerCode, String providerMessage, String lastExceptionSummary) {
        String errorMessage = StringUtils.defaultIfBlank(providerMessage, lastExceptionSummary);
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = "短信发送失败";
        }
        if (StringUtils.isBlank(providerCode)) {
            return errorMessage;
        }
        return providerCode + ":" + errorMessage;
    }

    private String buildTemplateParam(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private AliSmsConfig loadAliSmsConfig() {
        List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("ali_sms");
        return new AliSmsConfig(
                WeiXinConfigUtils.getWeiXinConfigragtionByEnName("sign_name", cfWeixinConfigs),
                WeiXinConfigUtils.getWeiXinConfigragtionByEnName("template_code", cfWeixinConfigs),
                WeiXinConfigUtils.getWeiXinConfigragtionByEnName("region_id", cfWeixinConfigs),
                WeiXinConfigUtils.getWeiXinConfigragtionByEnName("access_key_id", cfWeixinConfigs),
                WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret", cfWeixinConfigs)
        );
    }

    private static class AliSmsConfig {
        private final String signName;
        private final String templateCode;
        private final String regionId;
        private final String accessKeyId;
        private final String secret;

        private AliSmsConfig(String signName, String templateCode, String regionId, String accessKeyId, String secret) {
            this.signName = signName;
            this.templateCode = templateCode;
            this.regionId = regionId;
            this.accessKeyId = accessKeyId;
            this.secret = secret;
        }

        private String getSignName() {
            return signName;
        }

        private String getTemplateCode() {
            return templateCode;
        }

        private String getRegionId() {
            return regionId;
        }

        private String getAccessKeyId() {
            return accessKeyId;
        }

        private String getSecret() {
            return secret;
        }
    }

    private static class SmsSendResult {
        private final boolean success;
        private final String providerCode;
        private final String providerMessage;
        private final String lastExceptionSummary;

        private SmsSendResult(boolean success, String providerCode, String providerMessage, String lastExceptionSummary) {
            this.success = success;
            this.providerCode = providerCode;
            this.providerMessage = providerMessage;
            this.lastExceptionSummary = lastExceptionSummary;
        }

        private boolean isSuccess() {
            return success;
        }

        private String getProviderCode() {
            return providerCode;
        }

        private String getProviderMessage() {
            return providerMessage;
        }

        private String getLastExceptionSummary() {
            return lastExceptionSummary;
        }
    }
}
