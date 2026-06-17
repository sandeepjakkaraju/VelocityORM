/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.vet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.query.Query;

@Repository
public class VetRepositoryImpl implements VetRepository {

	private final VelocityORM orm;

	private final com.velocityorm.core.repository.Repository<Vet, Integer> vetRepo;

	public VetRepositoryImpl(VelocityORM orm) {
		this.orm = orm;
		this.vetRepo = orm.repository(Vet.class);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Vet> findAll() {
		try {
			List<Vet> vets = vetRepo.findAll();
			for (Vet vet : vets) {
				loadSpecialties(vet);
			}
			return vets;
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to find vets", ex);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Vet> findAll(Pageable pageable) {
		try {
			long total = vetRepo.query().count();
			List<Vet> vets = vetRepo.query()
				.orderBy(Vet::getLastName, Query.Direction.ASC)
				.limit(pageable.getPageSize())
				.offset((int) pageable.getOffset())
				.list();
			for (Vet vet : vets) {
				loadSpecialties(vet);
			}
			return new PageImpl<>(vets, pageable, total);
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to find vets", ex);
		}
	}

	private void loadSpecialties(Vet vet) throws SQLException {
		vet.getSpecialtiesInternal().clear();
		String sql = "SELECT s.id, s.name FROM specialties s INNER JOIN vet_specialties vs ON s.id = vs.specialty_id WHERE vs.vet_id = ? ORDER BY s.name";
		Connection conn = orm.getDataSource().getConnection();
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, vet.getId());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Specialty specialty = new Specialty();
					specialty.setId(rs.getInt("id"));
					specialty.setName(rs.getString("name"));
					vet.addSpecialty(specialty);
				}
			}
		}
		finally {
			if (conn.getAutoCommit()) {
				conn.close();
			}
		}
	}

}
