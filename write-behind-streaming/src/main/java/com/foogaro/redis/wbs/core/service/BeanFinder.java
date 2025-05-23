package com.foogaro.redis.wbs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


@Component
public class BeanFinder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

//    private final ListableBeanFactory listableBeanFactory;
    private Map<String, ?> allBeans;

    public BeanFinder(ListableBeanFactory listableBeanFactory) {
//        this.listableBeanFactory = listableBeanFactory;
        this.allBeans = listableBeanFactory.getBeansOfType(Object.class);
    }

    public <T> List<Repository<T, ?>> findRepositoriesForEntity(Class<T> entityClass, Class<?> repositoryClass) {
//        Map<String, ? extends Object> repositoryBeans = listableBeanFactory.getBeansOfType(repositoryClass);
        return allBeans.values()
                .stream()
                .filter(bean -> bean instanceof Repository)
                .map(bean -> (Repository<T, ?>) bean)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T> List<Repository<T, ?>> findRepositoriesForEntity(Class<?> repositoryClass) {
//        Map<String, ? extends Object> repositoryBeans = listableBeanFactory.getBeansOfType(repositoryClass);
        return allBeans.values()
                .stream()
                .filter(bean -> bean instanceof Repository)
                .map(bean -> (Repository<T, ?>) bean)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T> List<Repository<T, ?>> findRepositoriesForEntity(String repositoryClassName) {
//        allBeans = listableBeanFactory.getBeansOfType(Object.class);
        List<Repository<T, ?>> repositories = allBeans.values()
                .stream()
                .filter(bean -> {
                    Class<?> beanClass = bean.getClass();
                    // Check if the bean is a Spring Data JPA proxy
                    boolean isProxy = beanClass.getName().contains("$Proxy") || 
                                    beanClass.getName().contains("$JdkDynamicAopProxy");
                    
                    // Get the actual interface class if it's a proxy
                    Class<?> actualClass = isProxy ? 
                        Arrays.stream(beanClass.getInterfaces())
                            .filter(i -> i.getSimpleName().equals(repositoryClassName))
                            .findFirst()
                            .orElse(beanClass) : 
                        beanClass;
                    
                    boolean nameMatches = actualClass.getSimpleName().equals(repositoryClassName);
                    boolean isRepository = Repository.class.isAssignableFrom(actualClass);
                    logger.trace("Bean: {} - Is proxy: {}, Actual class: {}, Name matches: {}, Is repository: {}",
                        beanClass.getName(), isProxy, actualClass.getName(), nameMatches, isRepository);
                    return nameMatches && isRepository;
                })
                .map(bean -> (Repository<T, ?>) bean)
                .collect(Collectors.toList());
        return repositories;
    }

    public <T> Class<?> getIdType(Repository<T, ?> repository) {
        return Arrays.stream(repository.getClass().getInterfaces())
                .filter(i -> Repository.class.isAssignableFrom(i))
                .filter(i -> i.getGenericInterfaces().length > 0)
                .map(i -> i.getGenericInterfaces()[0])
                .filter(type -> type instanceof ParameterizedType)
                .map(type -> (ParameterizedType) type)
                .map(paramType -> paramType.getActualTypeArguments()[1])
                .filter(type -> type instanceof Class)
                .map(type -> (Class<?>) type)
                .findFirst()
                .orElse(null);
    }

    public Object createId(Class<?> idType, String value) {
        Objects.requireNonNull(idType, "ID type cannot be null");
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ID value cannot be null or empty");
        }
        try {
            if (idType == String.class) return value;
            if (idType == UUID.class) return UUID.fromString(value);
            if (Number.class.isAssignableFrom(idType)) {
                if (idType == Integer.class) return Integer.valueOf(value);
                if (idType == Long.class) return Long.valueOf(value);
            }
            Constructor<?> constructor = idType.getConstructor(String.class);
            return constructor.newInstance(value);
        } catch (Exception e) {
            logger.error("Failed to create ID of type {} with value {}", idType, value, e);
            throw new IllegalArgumentException("Cannot create ID", e);
        }
    }

    public <T, P> void executeOperation(Repository<T, ?> repository, P param,
                                        BiConsumer<CrudRepository<T, ?>, P> operation) {
        CrudRepository<T, ?> crudRepo = asCrudRepository(repository);
        operation.accept(crudRepo, param);
    }

    public <T, ID> void executeIdOperation(Repository<T, ?> repository, String idValue,
                                           BiConsumer<CrudRepository<T, ID>, ID> operation) {
        Class<?> idType = getIdType(repository);
        @SuppressWarnings("unchecked")
        ID id = (ID) createId(idType, idValue);

        CrudRepository<T, ID> crudRepo = asCrudRepository(repository);
        operation.accept(crudRepo, id);
    }

    @SuppressWarnings("unchecked")
    public <T, ID> CrudRepository<T, ID> asCrudRepository(Repository<T, ?> repository) {
        if (!(repository instanceof CrudRepository)) {
            throw new IllegalArgumentException("Repository must implement CrudRepository");
        }
        return (CrudRepository<T, ID>) repository;
    }
}
