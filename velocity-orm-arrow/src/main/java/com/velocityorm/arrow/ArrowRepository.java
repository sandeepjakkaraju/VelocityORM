package com.velocityorm.arrow;

import com.velocityorm.core.metadata.EntityMeta;
import com.velocityorm.core.repository.BaseRepository;
import com.velocityorm.core.VelocityORM;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ArrowRepository<T, ID> extends BaseRepository<T, ID> {

    public ArrowRepository(VelocityORM orm, EntityMeta<T, ID> meta) {
        super(orm, meta);
    }

    /**
     * Executes a SELECT * query and returns the entire result set serialized as an Arrow IPC byte array.
     */
    public byte[] findAllAsArrowIPC() throws SQLException {
        try {
            String sql = "SELECT * FROM " + dialect.quoteIdentifier(meta.getTableName());
            try (PreparedStatement ps = getSession().getConnection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                return ArrowMapper.toArrowIPC(rs);
            }
        } catch (Exception e) {
            throw new SQLException("Failed to map result set to Arrow format", e);
        }
    }
}
