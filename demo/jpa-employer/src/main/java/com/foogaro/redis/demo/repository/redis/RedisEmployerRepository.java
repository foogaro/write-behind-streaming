package com.foogaro.redis.demo.repository.redis;

import com.foogaro.redis.demo.entity.Employer;
import com.redis.om.spring.repository.RedisEnhancedRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RedisEmployerRepository extends RedisEnhancedRepository<Employer, Long> {

    Employer findByEmail(String email);

}