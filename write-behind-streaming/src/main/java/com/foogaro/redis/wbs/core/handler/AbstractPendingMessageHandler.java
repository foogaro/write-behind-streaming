package com.foogaro.redis.wbs.core.handler;

import com.foogaro.redis.wbs.core.exception.AcknowledgeMessageException;
import com.foogaro.redis.wbs.core.exception.ProcessMessageException;
import com.foogaro.redis.wbs.core.processor.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.foogaro.redis.wbs.core.Misc.*;

public abstract class AbstractPendingMessageHandler<T, R> implements MessageHandler<T, R> {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${wb.stream.listener.pel.max-attempts:3}")
    protected int MAX_ATTEMPTS;
    @Value("${wb.stream.listener.pel.max-retention:120000}")
    protected long MAX_RETENTION;
    @Value("${wb.stream.listener.pel.batch-size:50}")
    protected int BATCH_SIZE;
    @Value("${wb.stream.listener.pel.fixed-delay:300000}")
    protected final long fixedDelay = 300000;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final Class<T> entityClass;
    private final Class<R> repositoryClass;

    public abstract Processor<T, R> getProcessor();

    @SuppressWarnings("unchecked")
    public AbstractPendingMessageHandler() {
        super();
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.repositoryClass = (Class<R>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    }

    public RedisTemplate<String, String> getRedisTemplate() {
        return redisTemplate;
    }

    public Class<T> getEntityClass() {
        return this.entityClass;
    }

    public Class<R> getRepositoryClass() {
        return repositoryClass;
    }

    @Scheduled(fixedDelay = fixedDelay)
    public void processPendingMessages() {
        String streamKey = getStreamKey(entityClass);
        String groupName = getConsumerGroup(repositoryClass);
        String consumerName = getConsumerName(entityClass, repositoryClass);

        try {
            PendingMessagesSummary pendingSummary = redisTemplate.opsForStream()
                    .pending(streamKey, groupName);

            if (pendingSummary != null && pendingSummary.getTotalPendingMessages() > 0) {
                logger.info("Found {} pending messages for group {}",
                        pendingSummary.getTotalPendingMessages(), groupName);

                PendingMessages pendingMessages = redisTemplate.opsForStream()
                        .pending(streamKey,
                                Consumer.from(groupName, consumerName),
                                Range.unbounded(),
                                BATCH_SIZE);

                if (pendingMessages != null) {
                    for (PendingMessage pm : pendingMessages) {
                        String messageId = pm.getIdAsString();
                        long elapsedTime = pm.getElapsedTimeSinceLastDelivery().toMillis();
                        logger.info("Message ID {} re-processing", messageId);

                        MapRecord<String, String, String> message = null;
                            List<MapRecord<String, Object, Object>> rawMessages =
                                    redisTemplate.opsForStream().range(streamKey,
                                            Range.closed(messageId, messageId));
                        List<MapRecord<String, String, String>> messages =
                                (rawMessages != null) ? rawMessages
                                    .stream()
                                    .map(this::convertMapRecord)
                                    .collect(Collectors.toList())
                                : Collections.emptyList();

                        if (!messages.isEmpty()) {
                            message = messages.get(0);
                            Long counter = incrementCounterKey(getCounterKey(message.getId().getValue()));
                            logger.debug("Attempts: {} - Elapsed time: {}", counter, elapsedTime);
                            try {
                                getProcessor().process(message);
                                getProcessor().acknowledge(message);
                                expireCounterKey(getCounterKey(message.getId().getValue()));
                                logger.info("Successfully processed pending message: {}", messageId);
                            } catch (ProcessMessageException e) {
                                logger.error("Error processing pending message: {} - {}", messageId, e.getMessage());
                                if (counter >= MAX_ATTEMPTS) {
                                    handleMessageFailure(message, "Too many attempts", new RuntimeException(e), getCounterKey(message.getId().getValue()));
                                    throw new RuntimeException(e);
                                }
                            } catch (AcknowledgeMessageException e) {
                                if (counter >= MAX_ATTEMPTS) {
                                    handleMessageFailure(message, "Long lasting message", new RuntimeException(e), getCounterKey(message.getId().getValue()));
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }
            } else {
                logger.debug("Pending messages not found for group {}", groupName);
            }
        } catch (Exception e) {
            logger.error("Error processing pending messages: {}", e.getMessage());
        }
    }

    private MapRecord<String, String, String> convertMapRecord(MapRecord<String, Object, Object> record) {
        Objects.requireNonNull(record, "Record cannot be null");
        Map<String, String> convertedMap = new HashMap<>();
        record.getValue().forEach((k, v) -> {
            convertedMap.put(String.valueOf(k),String.valueOf(v));
        });

        return StreamRecords.newRecord()
                .withId(record.getId())
                .ofMap(convertedMap)
                .withStreamKey(record.getStream());
    }

    private void handleMessageFailure(MapRecord<String, String, String> message,
                                      String dlqReason, Exception cause,
                                      String counterKey) {
        handleDLQ(message, dlqReason, cause);
        expireCounterKey(counterKey);
    }

    private void expireCounterKey(String counterKey) {
        Objects.requireNonNull(counterKey, "Counter key cannot be null");
        redisTemplate.expire(counterKey, Duration.ZERO);
    }

    private Long incrementCounterKey(String counterKey) {
        Objects.requireNonNull(counterKey, "Counter key cannot be null");
        Long incr =redisTemplate.opsForValue().increment(counterKey);
        if (incr != null) {
            redisTemplate.expire(counterKey, Duration.ofMillis(MAX_RETENTION));
        } else return 0L;
        return incr;
    }

    private String getCounterKey(String id) {
        return getStreamKey(entityClass) + KEY_SEPARATOR + id;
    }

    private void handleDLQ(MapRecord<String, String, String> message, String dlqReason, Exception e) {
        try {
            if (message != null) {
                dumpMessage(message);
                logger.error("Received error: {}", e.getMessage());
                String deadLetterKey = getDLQStreamKey(entityClass);
                Map<String, String> deadLetterMessage = new HashMap<>(message.getValue());
                deadLetterMessage.put("reason", dlqReason);
                deadLetterMessage.put("error", e.getMessage());
                deadLetterMessage.put("streamKey", message.getStream());
                deadLetterMessage.put("streamID", message.getId().getValue());
                deadLetterMessage.put("consumer", getConsumerName(entityClass, repositoryClass));
                deadLetterMessage.put("group", getConsumerGroup(repositoryClass));

                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .withId(RecordId.autoGenerate())
                                .ofMap(deadLetterMessage)
                                .withStreamKey(deadLetterKey)
                );
                logger.warn("Message {} moved to dead letter queue for manual processing.", message.getId());
                redisTemplate.opsForStream().acknowledge(getConsumerGroup(repositoryClass), message);
                logger.warn("And Message {} acknowledged.", message.getId());
            }
        } catch (Exception dlqError) {
            logger.error("Error while moving message to dead letter queue: {}", dlqError.getMessage());
        }
    }
}
