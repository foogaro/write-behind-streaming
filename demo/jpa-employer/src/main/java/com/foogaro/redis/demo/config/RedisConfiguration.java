package com.foogaro.redis.demo.config;

//import com.foogaro.redis.demo.listener.EmployerKeyExpirationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.listener.PatternTopic;
//import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.Jedis;

@Configuration
public class RedisConfiguration {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${spring.data.redis.host}") private String redis_host;
    @Value("${spring.data.redis.port}") private Integer redis_port;

    @Bean
    public JedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redis_host);
        config.setPort(redis_port);
        return new JedisConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(JedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new GenericToStringSerializer<>(String.class));
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

//    @Bean
//    public RedisMessageListenerContainer redisMessageListenerContainer(
//            JedisConnectionFactory redisConnectionFactory) {
//        logger.debug("Creating RedisMessageListenerContainer");
//        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
//        container.setConnectionFactory(redisConnectionFactory);
////        container.addMessageListener(employerKeyExpirationListener, new PatternTopic("__keyevent@*__:expired"));
//        logger.debug("Created RedisMessageListenerContainer");
//        return container;
//    }

    @Bean
    public Jedis jedis() {
        return new Jedis(redis_host, redis_port);
    }
}