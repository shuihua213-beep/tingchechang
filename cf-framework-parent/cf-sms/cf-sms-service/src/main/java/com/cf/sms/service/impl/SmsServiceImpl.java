package com.cf.sms.service.impl;

import com.alibaba.fastjson.JSON;
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
import com.cf.sms.dao.mapper.CfSmsMapper;
import com.cf.sms.domain.CfSms;
import com.cf.sms.domain.SmsFailRecord;
import com.cf.sms.service.SmsService;
import com.cf.sms.util.SmsRedisUtil;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 请在此填写描述
 *
 * @ClassName SmsServiceImpl
 * @Author 隔壁小王子 981011512@qq.com
 * @Date 2020/12/25/025 22:50
 * @Version 1.0
 **/
@Service(version = "1.0.0", loadbalance = "roundrobin")
public class SmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);
    
    private static final int MAX_RETRY_COUNT = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000;

    @Autowired
    private CfSmsMapper cfSmsMapper;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    SmsRedisUtil smsRedisUtil;
    @Reference(version = "1.0.0", retries = 0, timeout = 5000, check = false)
    private CfWeixinConfigService cfWeixinConfigService;

    @Override
    public void sendSms(String phone, Integer type) {
        checkSendFrequently(phone, type);
        String code = (int)((Math.random()*9+1)*100000)+"";
        String smsId = idWorker.nextId();
        cfSmsMapper.insert(new CfSms(smsId, phone, code, type, CfSms.SMS_STATUS_INIT, System.currentTimeMillis(),
                System.currentTimeMillis()+CfSms.SMS_CODE_VALID_TIME));

        List<CfWeixinConfig> cfWeixinConfigs = cfWeixinConfigService.getWeiXinLoginConfigragtion("ali_sms");
        String signName = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("sign_name", cfWeixinConfigs);
        String templateCode = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("template_code", cfWeixinConfigs);
        String regionId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("region_id", cfWeixinConfigs);
        String accessKeyId = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("access_key_id", cfWeixinConfigs);
        String secret = WeiXinConfigUtils.getWeiXinConfigragtionByEnName("secret", cfWeixinConfigs);

        try {
            sendSmsByAliWithRetry(smsId, phone, "{\"code\":\""+code+"\"}", signName, templateCode, regionId, accessKeyId, secret, "", "");
            updateSmsStatus(smsId, CfSms.SMS_STATUS_SENT);
        } catch (Exception e) {
            log.error("发送短信失败，smsId: {}, phone: {}", smsId, phone, e);
            updateSmsStatus(smsId, CfSms.SMS_STATUS_FAIL);
            saveFailRecord(smsId, phone, "{\"code\":\""+code+"\"}", signName, templateCode, regionId, accessKeyId, secret, "", "", e);
            ExceptionCast.cast(CommonCode.FAIL, e.getMessage());
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
        try {
            sendSmsByAliWithRetry(idWorker.nextId(), PhoneNumbers, TemplateParam, signName, templateCode, regionId, accessKeyId, secret, SmsUpExtendCode, OutId);
        } catch (Exception e) {
            log.error("发送短信失败，PhoneNumbers: {}", PhoneNumbers, e);
            ExceptionCast.cast(CommonCode.FAIL, e.getMessage());
        }
    }

    public void sendSmsByAliWithRetry(String smsId, String PhoneNumbers, String TemplateParam, String signName, String templateCode, String regionId, String accessKeyId, String secret, String SmsUpExtendCode, String OutId) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                CommonResponse response = sendSmsByAliOnce(PhoneNumbers, TemplateParam, signName, templateCode, regionId, accessKeyId, secret, SmsUpExtendCode, OutId);
                String responseData = response.getData();
                if (StringUtils.isNotEmpty(responseData)) {
                    com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(responseData);
                    String code = jsonObject.getString("Code");
                    if ("OK".equals(code)) {
                        return;
                    } else {
                        lastException = new RuntimeException("短信发送失败，运营商返回码：" + code);
                    }
                }
            } catch (ServerException e) {
                lastException = e;
            } catch (ClientException e) {
                lastException = e;
            } catch (Exception e) {
                lastException = e;
            }

            retryCount++;
            if (retryCount <= MAX_RETRY_COUNT) {
                try {
                    long delay = BASE_RETRY_DELAY_MS * (long) Math.pow(2, retryCount - 1);
                    log.warn("短信发送失败，将在 {}ms 后重试，当前重试次数: {}", delay, retryCount);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            throw new RuntimeException("短信发送失败，已重试 " + MAX_RETRY_COUNT + " 次", lastException);
        }
    }

    public CommonResponse sendSmsByAliOnce(String PhoneNumbers, String TemplateParam, String signName, String templateCode, String regionId, String accessKeyId, String secret, String SmsUpExtendCode, String OutId) throws Exception {
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

        return client.getCommonResponse(request);
    }

    private void updateSmsStatus(String smsId, Integer status) {
        CfSms cfSms = new CfSms();
        cfSms.setId(smsId);
        cfSms.setStatus(status);
        cfSmsMapper.updateByPrimaryKeySelective(cfSms);
    }

    private void saveFailRecord(String smsId, String phone, String templateParam, String signName, String templateCode, String regionId, String accessKeyId, String secret, String smsUpExtendCode, String outId, Exception e) {
        SmsFailRecord failRecord = new SmsFailRecord();
        failRecord.setId(smsId);
        failRecord.setPhone(phone);
        failRecord.setTemplateCode(templateCode);
        failRecord.setSignName(signName);
        failRecord.setTemplateParam(templateParam);
        failRecord.setRegionId(regionId);
        failRecord.setAccessKeyId(accessKeyId);
        failRecord.setSecret(secret);
        failRecord.setSmsUpExtendCode(smsUpExtendCode);
        failRecord.setOutId(outId);
        failRecord.setRetryCount(0);
        failRecord.setCreateTime(System.currentTimeMillis());
        failRecord.setLastRetryTime(System.currentTimeMillis());
        
        String errorSummary = e.getMessage();
        if (errorSummary != null && errorSummary.length() > 500) {
            errorSummary = errorSummary.substring(0, 500);
        }
        failRecord.setErrorSummary(errorSummary);
        
        smsRedisUtil.saveFailRecord(failRecord);
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


        //验证码腾讯图形验证码
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

//        String s = DescribeCaptchaResultResponse.toJsonString(resp);

    }
}
