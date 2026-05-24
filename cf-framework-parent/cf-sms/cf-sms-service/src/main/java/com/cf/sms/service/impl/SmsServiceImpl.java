package com.cf.sms.service.impl;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.cf.framework.domain.response.CommonCode;
import com.cf.framework.domain.response.ResponseResult;
import com.cf.framework.domain.sms.response.SmsCode;
import com.cf.framework.domain.ucenter.response.UcenterCode;
import com.cf.framework.exception.ExceptionCast;
import com.cf.framework.utils.HttpClient;
import com.cf.framework.utils.IdWorker;
import com.cf.framework.utils.RedisUtils;
import com.cf.sms.dao.mapper.CfSmsMapper;
import com.cf.sms.domain.CfSms;
import com.cf.sms.domain.SmsFailedRecord;
import com.cf.sms.service.SmsService;
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
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service(version = "1.0.0", loadbalance = "roundrobin")
public class SmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);

    @Autowired
    private CfSmsMapper cfSmsMapper;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisUtils redisUtils;
    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private CfWeixinConfigService cfWeixinConfigService;

    @Override
    public void sendSms(String phone, Integer type) {
        sendSmsWithRetry(phone, type);
    }

    @Override
    public void sendSmsWithRetry(String phone, Integer type) {
        checkSendFrequently(phone, type);
        String code = (int)((Math.random()*9+1)*100000)+"";
        long smsId = idWorker.nextId();
        cfSmsMapper.insert(new CfSms(String.valueOf(smsId),phone,code,type,CfSms.SMS_STATUS_PENDING,System.currentTimeMillis(),
                System.currentTimeMillis()+CfSms.SMS_CODE_VALID_TIME));

        List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("ali_sms");
        String signName = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("sign_name", cfWeixinConfigs);
        String templateCode = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("template_code", cfWeixinConfigs);
        String regionId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("region_id", cfWeixinConfigs);
        String accessKeyId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("access_key_id", cfWeixinConfigs);
        String secret = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret", cfWeixinConfigs);

        boolean success = sendSmsByAliWithRetry(phone,"{\"code\":\""+code+"\"}",signName,templateCode,regionId,accessKeyId,secret,"","", type);

        if (success) {
            cfSmsMapper.updateStatusByPrimaryKey(String.valueOf(smsId), CfSms.SMS_STATUS_SENT);
            redisUtils.removeSmsFailedRecord(phone);
        }
    }

    @Override
    public void checkSendFrequently(String phone, Integer type) {
        List<CfSms> lastSendLog = cfSmsMapper.getLastSendLog(phone, type);
        if(lastSendLog!=null && lastSendLog.size()>0 && lastSendLog.get(0).getCreateTime()+CfSms.SMS_SEND_FREQUENTLY_LIMIT_TIME>System.currentTimeMillis()){
            ExceptionCast.cast(SmsCode.SMS_SEND_FREQUENTLY);
        }
    }

    @Override
    public void sendSmsByAli(String PhoneNumbers, String TemplateParam, String signName, String templateCode, String regionId, String accessKeyId, String secret, String SmsUpExtendCode, String OutId) {

        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, secret);
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", regionId);
        if(StringUtils.isNotEmpty(PhoneNumbers)){
            request.putQueryParameter("PhoneNumbers", PhoneNumbers);
        }
        if(StringUtils.isNotEmpty(signName)){
            request.putQueryParameter("SignName", signName);
        }
        if(StringUtils.isNotEmpty(templateCode)){
            request.putQueryParameter("TemplateCode", templateCode);
        }
        if(StringUtils.isNotEmpty(TemplateParam)){
            request.putQueryParameter("TemplateParam", TemplateParam);
        }
        if(StringUtils.isNotEmpty(SmsUpExtendCode)){
            request.putQueryParameter("SmsUpExtendCode", SmsUpExtendCode);
        }
        if(StringUtils.isNotEmpty(OutId)){
            request.putQueryParameter("OutId", OutId);
        }

        try {
            CommonResponse response = client.getCommonResponse(request);
        } catch (ServerException e) {
            ExceptionCast.cast(CommonCode.FAIL, e.getMessage());
        } catch (ClientException e) {
            ExceptionCast.cast(CommonCode.FAIL, e.getMessage());
        }
    }

    @Override
    public boolean sendSmsByAliWithRetry(String PhoneNumbers, String TemplateParam, String signName, String templateCode,
                                          String regionId, String accessKeyId, String secret, String SmsUpExtendCode, String OutId, Integer type) {
        int retryCount = 0;
        int maxRetries = SmsFailedRecord.MAX_RETRY_COUNT;
        Exception lastException = null;

        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, secret);
        IAcsClient client = new DefaultAcsClient(profile);

        while (retryCount < maxRetries) {
            try {
                CommonRequest request = new CommonRequest();
                request.setMethod(MethodType.POST);
                request.setDomain("dysmsapi.aliyuncs.com");
                request.setVersion("2017-05-25");
                request.setAction("SendSms");
                request.putQueryParameter("RegionId", regionId);
                if(StringUtils.isNotEmpty(PhoneNumbers)){
                    request.putQueryParameter("PhoneNumbers", PhoneNumbers);
                }
                if(StringUtils.isNotEmpty(signName)){
                    request.putQueryParameter("SignName", signName);
                }
                if(StringUtils.isNotEmpty(templateCode)){
                    request.putQueryParameter("TemplateCode", templateCode);
                }
                if(StringUtils.isNotEmpty(TemplateParam)){
                    request.putQueryParameter("TemplateParam", TemplateParam);
                }
                if(StringUtils.isNotEmpty(SmsUpExtendCode)){
                    request.putQueryParameter("SmsUpExtendCode", SmsUpExtendCode);
                }
                if(StringUtils.isNotEmpty(OutId)){
                    request.putQueryParameter("OutId", OutId);
                }

                CommonResponse response = client.getCommonResponse(request);
                String responseData = response.getData();
                if (StringUtils.isNotEmpty(responseData) && responseData.contains("\"Code\":\"OK\"")) {
                    log.info("SMS send success, phone={}, retryCount={}", PhoneNumbers, retryCount);
                    return true;
                }

                log.warn("SMS send returned non-OK code, phone={}, response={}, retryCount={}", PhoneNumbers, responseData, retryCount);
                retryCount++;
                if (retryCount < maxRetries) {
                    long backoffMs = SmsFailedRecord.INITIAL_BACKOFF_MS * (1L << (retryCount - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                lastException = new RuntimeException("AliSMS returned: " + responseData);

            } catch (ServerException e) {
                log.warn("SMS ServerException, phone={}, errorCode={}, retryCount={}", PhoneNumbers, e.getErrCode(), retryCount);
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    long backoffMs = SmsFailedRecord.INITIAL_BACKOFF_MS * (1L << (retryCount - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (ClientException e) {
                log.warn("SMS ClientException, phone={}, errorCode={}, retryCount={}", PhoneNumbers, e.getErrCode(), retryCount);
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    long backoffMs = SmsFailedRecord.INITIAL_BACKOFF_MS * (1L << (retryCount - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        String carrierReturnCode = extractCarrierReturnCode(lastException);
        String exceptionSummary = extractExceptionSummary(lastException);

        redisUtils.saveSmsFailedRecord(PhoneNumbers, type, templateCode, carrierReturnCode,
                exceptionSummary, retryCount, SmsFailedRecord.FAILED_RECORD_TTL_SECONDS);

        if (type != null) {
            cfSmsMapper.updateStatusByPhoneAndType(PhoneNumbers, type, CfSms.SMS_STATUS_FAILED);
        }
        log.error("SMS send failed after {} retries, phone={}, type={}, templateCode={}, carrierCode={}, summary={}",
                retryCount, PhoneNumbers, type, templateCode, carrierReturnCode, exceptionSummary);
        return false;
    }

    private String extractCarrierReturnCode(Exception e) {
        if (e == null) {
            return "UNKNOWN";
        }
        if (e instanceof ServerException) {
            return "ServerException:" + ((ServerException) e).getErrCode();
        }
        if (e instanceof ClientException) {
            return "ClientException:" + ((ClientException) e).getErrCode();
        }
        return e.getClass().getSimpleName();
    }

    private String extractExceptionSummary(Exception e) {
        if (e == null) {
            return "No exception captured";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String fullStack = sw.toString();
        String[] lines = fullStack.split("\\n");
        StringBuilder summary = new StringBuilder();
        int maxLines = Math.min(lines.length, 5);
        for (int i = 0; i < maxLines; i++) {
            if (summary.length() > 0) {
                summary.append("\\n");
            }
            summary.append(lines[i].trim());
        }
        if (lines.length > maxLines) {
            summary.append("\\n... (truncated, total ").append(lines.length).append(" lines)");
        }
        return summary.toString();
    }

    @Override
    public void checkCode(String phone, String code, Integer type) {
        String phoneSmsCheckCounts = null;
        String redisKey = phone+"_"+type;
        try {
            phoneSmsCheckCounts = stringRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            e.printStackTrace();
            ExceptionCast.cast(CommonCode.SERVER_ERROR, e.getMessage());
        }
        if(phoneSmsCheckCounts!=null && Integer.parseInt(phoneSmsCheckCounts)>=5){
            ExceptionCast.cast(SmsCode.CHECKING_TOO_FREQUENTLY);
        }

        int i = cfSmsMapper.updateLastValidSmsCodeStatus(phone, code, type, System.currentTimeMillis());
        if(i==0){
            if(phoneSmsCheckCounts!=null){
                Long expire = stringRedisTemplate.getExpire(phone, TimeUnit.MILLISECONDS);
                Long time = expire>0?expire:300000L;
                stringRedisTemplate.boundValueOps(redisKey).set((Integer.parseInt(phoneSmsCheckCounts)+1)+"",time, TimeUnit.MILLISECONDS);
            }else{
                stringRedisTemplate.boundValueOps(redisKey).set("1",300000L, TimeUnit.MILLISECONDS);
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
        String captchaAppId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName(platform+"_captcha_app_id", cfWeixinConfigs);
        String appSecretKey = WeiXinConfigUtils.getWeiXinConfigragtionByEnName(platform+"_app_secret_key", cfWeixinConfigs);


        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("captcha.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        CaptchaClient client = new CaptchaClient(cred, "", clientProfile);

        if(StringUtils.isNotEmpty(randstr)){
            DescribeCaptchaResultRequest describeCaptchaResultRequest = new DescribeCaptchaResultRequest();
            describeCaptchaResultRequest.setCaptchaType(9L);
            describeCaptchaResultRequest.setTicket(ticket);
            describeCaptchaResultRequest.setUserIp(ip);
            describeCaptchaResultRequest.setCaptchaAppId(new Long(captchaAppId));
            describeCaptchaResultRequest.setAppSecretKey(appSecretKey);
            describeCaptchaResultRequest.setRandstr(randstr);
            DescribeCaptchaResultResponse describeCaptchaResultResponse = client.DescribeCaptchaResult(describeCaptchaResultRequest);
            if(describeCaptchaResultResponse.getCaptchaCode()!=1){
                ExceptionCast.cast(UcenterCode.CAPTCHA_NOT_MATCH);
            }
        }else{
            DescribeCaptchaMiniResultRequest req = new DescribeCaptchaMiniResultRequest();
            req.setCaptchaType(9L);
            req.setTicket(ticket);
            req.setUserIp(ip);
            req.setCaptchaAppId(new Long(captchaAppId));
            req.setAppSecretKey(appSecretKey);
            DescribeCaptchaMiniResultResponse resp = client.DescribeCaptchaMiniResult(req);
            if(resp.getCaptchaCode()!=1){
                ExceptionCast.cast(UcenterCode.CAPTCHA_NOT_MATCH);
            }
        }

    }
}