package com.weweibuy.framework.samples.mq.consumer;

import com.weweibuy.framework.rocketmq.annotation.Payload;
import com.weweibuy.framework.rocketmq.annotation.RocketConsumerHandler;
import com.weweibuy.framework.rocketmq.annotation.RocketListener;
import com.weweibuy.framework.samples.message.SampleUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author durenhao
 * @date 2019/12/29 10:26
 **/
@Slf4j
@Component
@RocketListener(topic = "TEST_SAMPLE_01", group = "TEST_SAMPLE_01_C_GROUP")
public class SampleConsumer2 {

    @RocketConsumerHandler(tags = "QQQ")
    public void onMessage(@Payload SampleUser user) {
        log.info("收到消息: {}", user);
    }


}
