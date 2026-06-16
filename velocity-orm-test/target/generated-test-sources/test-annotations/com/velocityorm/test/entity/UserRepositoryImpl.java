package com.velocityorm.test.entity;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.metadata.EntityMeta;
import com.velocityorm.core.repository.BaseRepository;

public class UserRepositoryImpl extends BaseRepository<User, java.lang.Long> {
    public UserRepositoryImpl(VelocityORM orm, EntityMeta<User, java.lang.Long> meta) {
        super(orm, meta);
    }
}
