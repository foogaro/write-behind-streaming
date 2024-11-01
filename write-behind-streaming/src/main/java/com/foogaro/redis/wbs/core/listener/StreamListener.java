package com.foogaro.redis.wbs.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

/***
 * Is the interface used by the `AbstractStreamListener` class to define the methods to implement.
 */
public interface StreamListener {

    void onMessage(MapRecord<String, String, String> message);
    RedisTemplate<String, String> getRedisTemplate();
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> getStreamMessageListenerContainer();
    ObjectMapper getObjectMapper();

}
