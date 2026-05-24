package com.cf.sms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAutoConfiguration()
@ComponentScan(basePackages = {"com.cf.sms.service","com.cf.sms.dao","com.cf.sms.task","com.cf.framework"})
@MapperScan("com.cf.sms.dao.mapper")
@EnableScheduling
public class SmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsApplication.class, args);
    }
}
