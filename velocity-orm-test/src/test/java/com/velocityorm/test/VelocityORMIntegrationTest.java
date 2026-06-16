package com.velocityorm.test;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.cache.CaffeineCacheProvider;
import com.velocityorm.core.dialect.PostgresDialect;
import com.velocityorm.core.repository.Repository;
import com.velocityorm.core.security.EncryptionService;
import com.velocityorm.core.tx.SessionContext;
import com.velocityorm.core.tx.TransactionManager;
import com.velocityorm.core.tx.TransactionStatus;
import com.velocityorm.test.entity.User;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class VelocityORMIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static VelocityORM orm;
    private static Repository<User, Long> userRepository;

    @BeforeAll
    public static void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        orm = new VelocityORM.Builder()
                .dataSource(dataSource)
                .dialect(new PostgresDialect())
                .cacheProvider(new CaffeineCacheProvider())
                .encryptionService(new EncryptionService("my_secret_key_1234567890123456"))
                .build();

        orm.bootstrap(List.of(User.class));
        userRepository = orm.repository(User.class);
    }

    @AfterAll
    public static void teardown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    public void clean() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        }
        SessionContext.clear();
    }

    @Test
    public void testCrudAndCaching() throws Exception {
        User user = new User();
        user.setName("John Doe");
        user.setEmail("john@example.com");

        User saved = userRepository.save(user);
        assertNotNull(saved.getId());
        assertEquals("John Doe", saved.getName());

        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());
        assertEquals("john@example.com", found.get().getEmail());

        found.get().setName("John Smith");
        userRepository.update(found.get());

        Optional<User> updated = userRepository.findById(saved.getId());
        assertTrue(updated.isPresent());
        assertEquals("John Smith", updated.get().getName());

        userRepository.delete(saved.getId());
        assertFalse(userRepository.findById(saved.getId()).isPresent());
    }

    @Test
    public void testQueryBuilder() throws Exception {
        User u1 = new User();
        u1.setName("Alice");
        u1.setEmail("alice@example.com");
        userRepository.save(u1);

        User u2 = new User();
        u2.setName("Bob");
        u2.setEmail("bob@example.com");
        userRepository.save(u2);

        List<User> list = userRepository.query()
                .where(User::getName)
                .eq("Alice")
                .list();

        assertEquals(1, list.size());
        assertEquals("Alice", list.get(0).getName());

        long count = userRepository.query().count();
        assertEquals(2, count);
    }

    @Test
    public void testBatchInsert() throws Exception {
        User u1 = new User();
        u1.setName("User 1");
        u1.setEmail("u1@example.com");

        User u2 = new User();
        u2.setName("User 2");
        u2.setEmail("u2@example.com");

        userRepository.batchInsert(List.of(u1, u2));

        long count = userRepository.query().count();
        assertEquals(2, count);
    }

    @Test
    public void testTransactionalRollback() throws Exception {
        TransactionManager txManager = orm.getTransactionManager();
        TransactionStatus status = txManager.getTransaction(null);

        try {
            User u = new User();
            u.setName("Rollback User");
            u.setEmail("rollback@example.com");
            userRepository.save(u);

            txManager.rollback(status, new RuntimeException("Force rollback"));
        } catch (Exception e) {
            txManager.rollback(status, e);
        }

        long count = userRepository.query().count();
        assertEquals(0, count);
    }
}
