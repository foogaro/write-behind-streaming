package com.foogaro.redis.wbs.core.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foogaro.redis.wbs.core.Misc;
import com.foogaro.redis.wbs.core.exception.AcknowledgeMessageException;
import com.foogaro.redis.wbs.core.exception.ProcessMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.foogaro.redis.wbs.core.Misc.*;

public abstract class AbstractProcessor<T, R> implements Processor<T, R> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public RedisTemplate<String, String> getRedisTemplate() {
        return redisTemplate;
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    protected final MapRecord<String, String, String> record;
    protected int priority;
    private Class<T> entityClass;
    private Class<R> repositoryClass;

    @SuppressWarnings("unchecked")
    protected AbstractProcessor() {
        this.record = null;
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.repositoryClass = (Class<R>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    }

    @SuppressWarnings("unchecked")
    public AbstractProcessor(MapRecord<String, String, String> record) {
        this.record = record;
        this.priority = Integer.MAX_VALUE;
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.repositoryClass = (Class<R>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    }

    @SuppressWarnings("unchecked")
    public AbstractProcessor(MapRecord<String, String, String> record, int priority) {
        this.record = record;
        this.priority = priority;
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.repositoryClass = (Class<R>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    }

    public T convertToEntity(String content) throws JsonProcessingException {
        return getObjectMapper().readValue(content, getEntityClass());
    }
    public MapRecord<String, String, String> getRecord() {
        return record;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }
    public Class<R> getRepositoryClass() {
        return repositoryClass;
    }

    public List<Repository<T, ?>> getRepositories() {
//        return getRepositoryFinder().findRepositoriesForEntity(getEntityClass(), getRepositoryClass());
//        return getRepositoryFinder().findRepositoriesForEntity(getRepositoryClass());
        return getRepositoryFinder().findRepositoriesForEntity(getRepositoryClass().getSimpleName());
    }

    public void process(final MapRecord<String, String, String> record) throws ProcessMessageException {
        List<Repository<T, ?>> repositories = getRepositories();
        AtomicReference<Exception> exception = new AtomicReference<>();
        repositories.forEach(repo -> {
            try {
                String content = record.getValue().get(EVENT_CONTENT_KEY);
                String operation = record.getValue().get(EVENT_OPERATION_KEY);
                logger.debug("Processing message: {}", record.getId());
                logger.debug("Processing EVENT_CONTENT_KEY: {}", content);
                logger.debug("Processing EVENT_OPERATION_KEY: {}", operation);
                if (Misc.Operation.DELETE.getValue().equals(operation)) {
                    logger.trace("Deleting message: {}", record.getId());
                    getRepositoryFinder().executeIdOperation(repo, content, CrudRepository::deleteById);
                    logger.trace("Deleted message: {}", record.getId());
                } else {
                    logger.trace("Saving message: {}", record.getId());
                    getRepositoryFinder().executeOperation(repo, convertToEntity(content), CrudRepository::save);
                    logger.trace("Saved message: {}", record.getId());
                }
                logger.info("Processed message: {}", record.getId());
            } catch (Exception e) {
                logger.error("Error processing message: {}\n{}", record.getId(), e.getMessage());
                exception.set(e);
            }
        });
        if (exception.get() != null) {
            throw new ProcessMessageException(exception.get());
        }
    }

    public void acknowledge(final MapRecord<String, String, String> record) throws AcknowledgeMessageException {
        List<Repository<T, ?>> repositories = getRepositories();
        AtomicReference<Exception> exception = new AtomicReference<>();
        repositories.forEach(repo -> {
            try {
                logger.debug("Acknowledging message: {}", record.getId());
                getRedisTemplate().opsForStream().acknowledge(getConsumerGroup(repositoryClass), record);
                logger.debug("Acknowledged message: {} for group: {}", record.getId(), getConsumerGroup(repositoryClass));
            } catch (Exception e) {
                logger.error("Error acknowledging message: {}\n{}", record.getId(), e.getMessage());
                // will be picked up by the processPendingMessages method
                exception.set(e);
            }
        });
        if (exception.get() != null) {
            throw new AcknowledgeMessageException(exception.get());
        }
    }

}
