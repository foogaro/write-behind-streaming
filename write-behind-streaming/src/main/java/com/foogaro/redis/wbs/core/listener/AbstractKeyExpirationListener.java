package com.foogaro.redis.wbs.core.listener;

import com.foogaro.redis.wbs.core.service.WBSService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Optional;

public abstract class AbstractKeyExpirationListener<T> implements MessageListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    protected abstract WBSService<T> getService();
    protected abstract String getKeyPrefix();

    @PostConstruct
    private void init() {
        redisMessageListenerContainer.addMessageListener(this,
                new PatternTopic("__keyevent@*__:expired"));
        logger.info("{} initialized and listening for expired keys.", getClass().getSimpleName());
    }

    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody());
        logger.debug("Received message on pattern: {}", new String(pattern));
        logger.debug("Received message {} on channel: {}", key, new String(message.getChannel()));

        if (key.startsWith(getKeyPrefix())) {
            logger.debug("Processing expired key: {}", key);
            String id = key.substring(getKeyPrefix().length());
            Optional<T> reloadedEntity = getService().reloadById(id);
            logger.debug("Expired entity({}) reloaded: {}", key, reloadedEntity);
        } else {
            logger.debug("Ignoring expired key (prefix {} do not match with the key): {}", getKeyPrefix(), key);
        }
    }

}
