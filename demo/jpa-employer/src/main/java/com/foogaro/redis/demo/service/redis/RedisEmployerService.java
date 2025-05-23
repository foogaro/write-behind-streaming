package com.foogaro.redis.demo.service.redis;

import com.foogaro.redis.demo.entity.Employer;
import com.foogaro.redis.demo.repository.redis.RedisEmployerRepository;
import com.foogaro.redis.wbs.core.service.WBSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RedisEmployerService extends WBSService<Employer> {

    @Autowired
    private RedisEmployerRepository repository;

    public Iterable<Employer> findAll() {
        return repository.findAll();
    }

//    public Optional<Employer> findById(Long id) {
//        return repository.findById(id);
//    }

    public Employer findByEmail(String email) {
        return repository.findByEmail(email);
    }

}
