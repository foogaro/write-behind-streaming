package com.foogaro.redis.demo.listener;

import com.foogaro.redis.demo.service.redis.RedisEmployerService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class EmployerKeyExpirationListener implements MessageListener {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String KEY_PREFIX = "employer:";

    @Autowired
    private RedisEmployerService employerService;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @PostConstruct
    private void init() {
//        if (!redisMessageListenerContainer.isRunning()) {
            redisMessageListenerContainer.addMessageListener(this,
                    new PatternTopic("__keyevent@*__:expired"));
            logger.info("{} initialized and listening for expired keys.", getClass().getSimpleName());
//            redisMessageListenerContainer.start();
//        }
    }

    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody());
        logger.debug("Received message on pattern: {}", new String(pattern));
        logger.debug("Received message {} on channel: {}", key, new String(message.getChannel()));

        if (key.startsWith(KEY_PREFIX)) {
            logger.debug("Processing expired key: {}", key);
            String id = key.substring(KEY_PREFIX.length());
            employerService.reloadById(id);
        } else {
            logger.debug("Ignoring expired key (not an employer key): {}", key);
        }
    }

}