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
import java.util.*;

import static com.foogaro.redis.wbs.core.Misc.*;

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
                String recordId = writeBehindForInsert(entity);
                logger.debug("recordId: {}", entity);
            } else {
                String repositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
                Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
                if (repository instanceof KeyValueRepository) {
                    @SuppressWarnings("unchecked")
                    KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) repository;
                    entity = keyValueRepository.save(entity);
                    logger.debug("entity keyValueRepository: {}", entity);
                }
            }
        }
    }

    public Optional<T> findById(Object id) {
        Optional<T> entity = Optional.empty();
        if (Objects.nonNull(id)) {
            Map<Object, Object> entityMap = redisTemplate.opsForHash().entries(Misc.getEntityKeyPrefix(entityClass) + KEY_SEPARATOR + id.toString());
            if (!entityMap.isEmpty()) {
                try {
                    String json = objectMapper.writeValueAsString(entityMap);
                    entity = Optional.of(objectMapper.readValue(json, entityClass));
                    logger.debug("Entity found in Cache: {}", entity.get());
                    return entity;
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Error converting entity from Cache", e);
                }
            } else {
                if (annotationFinder.hasCacheAside(entityClass)) {
                    entity = cacheAside(id);
                    logger.debug("entity: {}", entity);
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
                String repositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
                Repository<Object, ?> repository = beanFinder.findRepositoriesForEntity(repositoryName).getFirst();
                if (repository instanceof KeyValueRepository) {
                    @SuppressWarnings("unchecked")
                    KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) repository;
                    keyValueRepository.deleteById(id);
                    logger.debug("Deleted from Cache by Id: {}", id);
                }
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
        logger.debug("Trying to load entity from JpaRepository");
        String repositoryName = "Jpa" + entityClass.getSimpleName() + "Repository";
        try {
//            List<Repository<T, ?>> repositories = beanFinder.findRepositoriesForEntity(Class.forName(repositoryName));
            List<Repository<T, ?>> repositories = beanFinder.findRepositoriesForEntity(repositoryName);
            if (!repositories.isEmpty()) {
                Repository<T, ?> repository = repositories.getFirst();
                if (repository instanceof CrudRepository) {
                    @SuppressWarnings("unchecked")
                    CrudRepository<T, Object> crudRepository = (CrudRepository<T, Object>) repository;
                    entity = crudRepository.findById(id);
                    logger.info("entity crudRepository: {}", entity);
                    if (entity.isPresent()) {
                        String redisRepositoryName = "Redis" + entityClass.getSimpleName() + "Repository";
                        Repository<Object, ?> cacheRepository = beanFinder.findRepositoriesForEntity(redisRepositoryName).getFirst();
                        if (cacheRepository instanceof KeyValueRepository) {
                            @SuppressWarnings("unchecked")
                            KeyValueRepository<T, Object> keyValueRepository = (KeyValueRepository<T, Object>) cacheRepository;
                            T savedEntity = keyValueRepository.save(entity.get());
                            entity = Optional.of(savedEntity);
                            logger.debug("entity keyValueRepository: {}", entity);
                        }
                    }
                }
            } else {
                logger.warn("No JPA repository found for entity {}: {}", entityClass.getSimpleName(), repositoryName);
            }
        } catch (Exception e) {
            logger.warn("JPA repository not found for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
        }
        return entity;
    }

}
