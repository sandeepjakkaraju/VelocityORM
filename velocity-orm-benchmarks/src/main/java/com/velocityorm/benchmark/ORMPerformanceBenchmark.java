package com.velocityorm.benchmark;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.dialect.PostgresDialect;
import com.velocityorm.core.repository.Repository;
import com.velocityorm.benchmark.entity.BenchmarkUser;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author sandeepkumarjakkaraju
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class ORMPerformanceBenchmark {

    private VelocityORM orm;
    private Repository<BenchmarkUser, Long> repository;

    @Setup
    public void setup() throws Exception {
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        Connection mockConnection = Mockito.mock(Connection.class);
        DatabaseMetaData mockMetaData = Mockito.mock(DatabaseMetaData.class);
        CallableStatement mockCallableStatement = Mockito.mock(CallableStatement.class);
        PreparedStatement mockPreparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet mockResultSet = Mockito.mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.supportsSavepoints()).thenReturn(true);
        when(mockConnection.prepareCall(anyString())).thenReturn(mockCallableStatement);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
        
        when(mockCallableStatement.execute()).thenReturn(true);
        when(mockCallableStatement.getResultSet()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getObject("id")).thenReturn(123L);
        when(mockResultSet.getLong("id")).thenReturn(123L);
        when(mockResultSet.getString("name")).thenReturn("Benchmark User");
        when(mockResultSet.getString("email")).thenReturn("bench@example.com");

        orm = new VelocityORM.Builder()
                .dataSource(mockDataSource)
                .dialect(new PostgresDialect())
                .build();

        orm.bootstrap(List.of(BenchmarkUser.class));
        repository = orm.repository(BenchmarkUser.class);
    }

    @Benchmark
    public Optional<BenchmarkUser> testFindById() throws SQLException {
        return repository.findById(123L);
    }

    @Benchmark
    public BenchmarkUser testInsert() throws SQLException {
        BenchmarkUser user = new BenchmarkUser();
        user.setName("New User");
        user.setEmail("new@example.com");
        return repository.save(user);
    }

    @Benchmark
    public List<BenchmarkUser> testQueryBuilder() throws SQLException {
        return repository.query()
                .where(BenchmarkUser::getName)
                .eq("Benchmark User")
                .list();
    }
}
