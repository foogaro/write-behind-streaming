package com.foogaro.redis.wbs.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foogaro.redis.wbs.core.Misc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.foogaro.redis.wbs.core.Misc.EVENT_CONTENT_KEY;
import static com.foogaro.redis.wbs.core.Misc.EVENT_OPERATION_KEY;

public abstract class WBSService<T> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Class<T> entityClass;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BeanFinder beanFinder;
    @Autowired
    private AnnotationFinder annotationFinder;

    @SuppressWarnings("unchecked")
    public WBSService() {
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.streamKey = Misc.getStreamKey(entityClass);
    }

    private final String streamKey;

    public void save(T entity) {
        if (Objects.nonNull(entity)) {
            if (annotationFinder.hasWriteBehind(entityClass)) {
                writeBehindForInsert(entity);
            } else {
                writeToCache(entity);
                logger.debug("Pattern WriteBehind not enabled for Entity {}", entityClass.getSimpleName());
            }
        }
    }

    public Optional<T> reloadById(Object id) {
        return cacheAside(id);
    }

    public Optional<T> findById(Object id) {
        Optional<T> entity = Optional.empty();
        if (Objects.nonNull(id)) {
            entity = readFromCache(id);
            if (entity.isPresent()) {
                return entity;
            } else {
                if (annotationFinder.hasCacheAside(entityClass) || annotationFinder.hasRefreshAhead(entityClass)) {
                    entity = cacheAside(id);
                    return entity;
                } else {
                    logger.debug("Pattern CacheAside not enabled for Entity {}", entityClass.getSimpleName());
                }
            }
        }
        return entity;
    }

    public void delete(Object id) {
        if (Objects.nonNull(id)) {
            if (annotationFinder.hasWriteBehind(entityClass)) {
                writeBehindForDelete(id);
                logger.debug("Deleted by Id: {}", id);
            } else {
                deleteFromCache(id);
                logger.debug("Pattern WriteBehind not enabled for Entity {}", entityClass.getSimpleName());
            }
        }
    }


    private String writeBehindForInsert(T entity) {
        try {
            String json = objectMapper.writeValueAsString(entity);
            Map<String, String> map = new HashMap<>();
            map.put(EVENT_CONTENT_KEY, json);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .withId(RecordId.autoGenerate())
                    .ofMap(map)
                    .withStreamKey(streamKey);
            String recordId = save(record);
            logger.debug("RecordId {} added for ingestion to the Stream {}", recordId, streamKey);
            return recordId;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String save(MapRecord<String, String, String> mapRecord) {
        RecordId recordId = redisTemplate.opsForStream().add(mapRecord);
        return Objects.nonNull(recordId) ? recordId.getValue() : null;
    }

    private void writeBehindForDelete(Object id) {
        Map<String, String> map = new HashMap<>();
        map.put(EVENT_CONTENT_KEY, id.toString());
        map.put(EVENT_OPERATION_KEY, Misc.Operation.DELETE.getValue());
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .withId(RecordId.autoGenerate())
                .ofMap(map)
                .withStreamKey(streamKey);
        RecordId recordId = redisTemplate.opsForStream().add(record);
        logger.debug("RecordId {} added for deletion to the Stream {}", Objects.nonNull(recordId) ? recordId.getValue() : "<null>", streamKey);
    }

    private Optional<T> cacheAside(Object id) {
        Optional<T> entity = Optional.empty();
        if (Objects.nonNull(id)) {
            entity = readFromDatabase(id);
            if (entity.isPresent()) {
                entity = writeToCache(entity.get());
            } else {
                logger.debug("Entity not found in Database: {}", id);
            }
        } else {
            logger.warn("Id is null for entity {}", entityClass.getSimpleName());
        }
        return entity;
    }

    private Optional<T> readFromCache(Object id) {
        Optional<T> entity = Optional.empty();
        String repositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
        Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
        if (repository instanceof KeyValueRepository) {
            @SuppressWarnings("unchecked")
            KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) repository;
            entity = keyValueRepository.findById(id);
            logger.debug("Entity read from cache: {}", entity);
        } else {
            logger.warn("Repository is not a KeyValueRepository: {}", repository.getClass().getSimpleName());
        }
        return entity;
    }

    private void deleteFromCache(Object id) {
        if (Objects.nonNull(id)) {
            Optional<T> entity = Optional.empty();
            String repositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
            Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
            if (repository instanceof KeyValueRepository) {
                @SuppressWarnings("unchecked")
                KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) repository;
                keyValueRepository.deleteById(id);
                logger.debug("Entity deleted from cache: {}", id);
            } else {
                logger.warn("Repository is not a KeyValueRepository: {}", repository.getClass().getSimpleName());
            }
        } else {
            logger.warn("Id is null for entity {}", entityClass.getSimpleName());
        }
    }

    private Optional<T> writeToCache(T entity) {
        if (Objects.nonNull(entity)) {
            String repositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
            Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
            if (repository instanceof KeyValueRepository) {
                @SuppressWarnings("unchecked")
                KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) repository;
                T savedEntity = keyValueRepository.save(entity);
                logger.debug("Entity written to cache: {}", savedEntity);
                return Optional.of(savedEntity);
            } else {
                logger.warn("Repository is not a KeyValueRepository: {}", repository.getClass().getSimpleName());
            }
        } else {
            logger.warn("Entity is null: {}", entity);
        }
        return Optional.empty();
    }

    private Optional<T> readFromDatabase(Object id) {
        Optional<T> entity = Optional.empty();
        String repositoryName = "Jpa" + entityClass.getSimpleName() + "Repository";
        Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
        if (repository instanceof CrudRepository) {
            @SuppressWarnings("unchecked")
            CrudRepository<T, Object> crudRepository = (CrudRepository<T, Object>) repository;
            entity = crudRepository.findById(id);
            logger.debug("Entity read from database: {}", entity);
        } else {
            logger.warn("Repository is not a CrudRepository: {}", repository.getClass().getSimpleName());
        }
        return entity;
    }

    private void deleteFromDatabase(Object id) {
        if (Objects.nonNull(id)) {
            String repositoryName = "Jpa" + entityClass.getSimpleName() + "Repository";
            Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
            if (repository instanceof CrudRepository) {
                @SuppressWarnings("unchecked")
                CrudRepository<T, Object> crudRepository = (CrudRepository<T, Object>) repository;
                crudRepository.deleteById(id);
                logger.debug("Entity deleted from database: {}", id);
            } else {
                logger.warn("Repository is not a CrudRepository: {}", repository.getClass().getSimpleName());
            }
        } else {
            logger.warn("Id is null for entity {}", entityClass.getSimpleName());
        }
    }

    private Optional<T> writeToDatabase(T entity) {
        if (Objects.nonNull(entity)) {
            String repositoryName = "Jpa" + entityClass.getSimpleName() + "Repository";
            Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
            if (repository instanceof CrudRepository) {
                @SuppressWarnings("unchecked")
                CrudRepository<T, Object> crudRepository = (CrudRepository<T, Object>) repository;
                T savedEntity = crudRepository.save(entity);
                logger.debug("Entity written to database: {}", savedEntity);
                return Optional.of(savedEntity);
            } else {
                logger.warn("Repository is not a CrudRepository: {}", repository.getClass().getSimpleName());
            }
        } else {
            logger.warn("Entity is null: {}", entity);
        }
        return Optional.empty();
    }

}
