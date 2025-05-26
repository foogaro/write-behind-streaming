package com.foogaro.redis.demo.service.redis;

import com.foogaro.redis.demo.entity.Employer;
import com.foogaro.redis.demo.repository.redis.RedisEmployerRepository;
import com.foogaro.redis.wbs.core.service.WBSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("redisEmployerService")
public class RedisEmployerService extends WBSService<Employer> {

    private final RedisEmployerRepository repository;

    @Autowired
    public RedisEmployerService(RedisEmployerRepository repository) {
        super();
        this.repository = repository;
    }

    public Iterable<Employer> findAll() {
        return repository.findAll();
    }

    public Optional<Employer> findById(Long id) {
        return super.findById(id);
    }

    public Employer findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public void save(Employer employer) {
        super.save(employer);
    }

    public void delete(Long id) {
        super.delete(id);
    }
}
