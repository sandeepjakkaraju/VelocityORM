package com.velocityorm.arrow;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.metadata.EntityMeta;

public class VelocityArrow {

    public static <T, ID> ArrowRepository<T, ID> repository(VelocityORM orm, Class<T> entityClass) {
        EntityMeta<T, ID> meta = orm.entityMeta(entityClass);
        return new ArrowRepository<>(orm, meta);
    }
}
