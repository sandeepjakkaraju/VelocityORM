package com.velocityorm.core;

import com.velocityorm.core.dialect.Dialect;
import com.velocityorm.core.metadata.EntityMeta;
import com.velocityorm.core.migration.MigrationManager;
import com.velocityorm.core.migration.ProcedureGenerator;
import com.velocityorm.core.migration.SchemaGenerator;
import com.velocityorm.core.repository.Repository;
import com.velocityorm.core.security.EncryptionService;
import com.velocityorm.core.tx.TransactionManager;
import com.velocityorm.core.cache.CacheProvider;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityORM {
    private final DataSource dataSource;
    private final Dialect dialect;
    private final TransactionManager transactionManager;
    private final CacheProvider cacheProvider;
    private final EncryptionService encryptionService;
    private final Map<Class<?>, Repository<?, ?>> repositoryCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, EntityMeta<?, ?>> entityMetas = new ConcurrentHashMap<>();

    private VelocityORM(Builder builder) {
        this.dataSource = builder.dataSource;
        this.dialect = builder.dialect;
        this.transactionManager = new TransactionManager(dataSource);
        this.cacheProvider = builder.cacheProvider;
        this.encryptionService = builder.encryptionService;
    }

    public DataSource getDataSource() { return dataSource; }
    public Dialect getDialect() { return dialect; }
    public TransactionManager getTransactionManager() { return transactionManager; }
    public CacheProvider getCacheProvider() { return cacheProvider; }
    public EncryptionService getEncryptionService() { return encryptionService; }

    @SuppressWarnings("unchecked")
    public <T, ID> Repository<T, ID> repository(Class<T> entityClass) {
        return (Repository<T, ID>) repositoryCache.computeIfAbsent(entityClass, clazz -> {
            try {
                EntityMeta<T, ID> meta = entityMeta(entityClass);
                String repoImplName = clazz.getName() + "RepositoryImpl";
                Class<?> repoImplClass = Class.forName(repoImplName);
                Constructor<?> ctor = repoImplClass.getConstructor(VelocityORM.class, EntityMeta.class);
                return (Repository<?, ?>) ctor.newInstance(this, meta);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load repository implementation for entity: " + clazz.getName(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T, ID> EntityMeta<T, ID> entityMeta(Class<T> entityClass) {
        return (EntityMeta<T, ID>) entityMetas.computeIfAbsent(entityClass, clazz -> {
            try {
                String metaName = clazz.getName() + "Meta";
                Class<?> metaClass = Class.forName(metaName);
                return (EntityMeta<?, ?>) metaClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load metadata class for entity: " + clazz.getName(), e);
            }
        });
    }

    public void bootstrap(List<Class<?>> entityClasses) throws Exception {
        MigrationManager mm = new MigrationManager(dataSource);
        try {
            mm.migrate();
        } catch (Exception e) {
            // Ignore if migrations location is empty
        }

        List<EntityMeta<?, ?>> metas = new ArrayList<>();
        for (Class<?> clazz : entityClasses) {
            metas.add(entityMeta(clazz));
        }

        try (Connection conn = dataSource.getConnection()) {
            SchemaGenerator sg = new SchemaGenerator(dialect);
            sg.generateSchema(conn, metas);

            ProcedureGenerator pg = new ProcedureGenerator(dialect);
            pg.generateProcedures(conn, metas);
        }
    }

    public static class Builder {
        private DataSource dataSource;
        private Dialect dialect;
        private CacheProvider cacheProvider;
        private EncryptionService encryptionService;

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder dialect(Dialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder cacheProvider(CacheProvider cacheProvider) {
            this.cacheProvider = cacheProvider;
            return this;
        }

        public Builder encryptionService(EncryptionService encryptionService) {
            this.encryptionService = encryptionService;
            return this;
        }

        public VelocityORM build() {
            if (dataSource == null) throw new IllegalStateException("DataSource must be configured");
            if (dialect == null) throw new IllegalStateException("Dialect must be configured");
            return new VelocityORM(this);
        }
    }
}
