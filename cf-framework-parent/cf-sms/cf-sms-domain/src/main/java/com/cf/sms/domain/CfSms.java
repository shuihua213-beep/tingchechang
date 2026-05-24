package com.cf.sms.domain;

import java.io.Serializable;

public class CfSms implements Serializable {

    final public static Integer SMS_TYPE_REGISTER = 1;
    final public static Integer SMS_TYPE_IDENTITY = 2;
    final public static Integer SMS_TYPE_NOTICE = 3;
    final public static Long SMS_SEND_FREQUENTLY_LIMIT_TIME = 60000L;
    final public static Long SMS_CODE_VALID_TIME = 300000L;
    final public static Integer STATUS_PENDING = 0;
    final public static Integer STATUS_SUCCESS = 1;
    final public static Integer STATUS_FAILED = 2;
    final public static Integer MAX_RETRY_COUNT = 3;

    private String id;

    private String phone;

    private String code;

    private Integer type;

    private Integer status;

    private Long createTime;

    private Long expireTime;

    private String errorCode;

    private String errorStack;

    private Integer retryCount;

    private Long lastRetryTime;

    private static final long serialVersionUID = 1L;

    public CfSms() {
    }

    public CfSms(String id, String phone, String code, Integer type, Integer status, Long createTime, Long expireTime) {
        this.id = id;
        this.phone = phone;
        this.code = code;
        this.type = type;
        this.status = status;
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.retryCount = 0;
    }

    public CfSms(String id, String phone, String code, Integer type, Integer status, Long createTime, Long expireTime, String errorCode, String errorStack, Integer retryCount, Long lastRetryTime) {
        this.id = id;
        this.phone = phone;
        this.code = code;
        this.type = type;
        this.status = status;
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.errorCode = errorCode;
        this.errorStack = errorStack;
        this.retryCount = retryCount;
        this.lastRetryTime = lastRetryTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? null : id.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim();
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorStack() {
        return errorStack;
    }

    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getLastRetryTime() {
        return lastRetryTime;
    }

    public void setLastRetryTime(Long lastRetryTime) {
        this.lastRetryTime = lastRetryTime;
    }
}