package com.cf.sms.config;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group:cf-sms-producer-group}")
    private String producerGroup;

    private DefaultMQProducer producer;

    @Bean
    public DefaultMQProducer defaultMQProducer() throws MQClientException {
        producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        producer.setRetryTimesWhenSendAsyncFailed(2);
        producer.start();
        log.info("RocketMQ producer started, nameServer={}, group={}", nameServer, producerGroup);
        return producer;
    }

    public void sendSmsEventAsync(String topic, String tags, Object messageBody) {
        if (producer == null) {
            log.warn("RocketMQ producer not initialized, skip sending message to topic={}", topic);
            return;
        }
        try {
            String jsonBody = JSON.toJSONString(messageBody);
            Message msg = new Message(topic, tags, jsonBody.getBytes(StandardCharsets.UTF_8));
            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("RocketMQ send success, topic={}, msgId={}", topic, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("RocketMQ send failed, topic={}", topic, e);
                }
            });
        } catch (Exception e) {
            log.error("RocketMQ send exception, topic={}", topic, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
            log.info("RocketMQ producer shutdown");
        }
    }
}